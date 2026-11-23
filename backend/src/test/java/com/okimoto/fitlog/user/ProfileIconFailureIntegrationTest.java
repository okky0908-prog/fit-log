package com.okimoto.fitlog.user;

import static com.okimoto.fitlog.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestImages;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * アイコンの保存の途中で S3・DB が失敗したときの後片付け（docs/api.md A-82、docs/test-plan.md 4.5）。S3 のクライアントと Repository
 * を、本物を包んだ偽物（spy）にして、決めた所で失敗させる
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileIconFailureIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @MockitoSpyBean private S3Client s3Client;
  @MockitoSpyBean private UserRepository userRepository;

  /** S3 に保存したキー（失敗したものは入らない） */
  private final List<String> storedKeys = new CopyOnWriteArrayList<>();

  private TestAccounts accounts;

  @BeforeEach
  void setUp() {
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
    Mockito.reset(s3Client, userRepository);
    storedKeys.clear();
  }

  @Test
  void S3に保存できなければ500で_表示名も変えない() throws Exception {
    Account me = accounts.signup();
    doThrow(SdkClientException.create("S3 につながらない"))
        .when(s3Client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    edit(me, TestImages.png())
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_FAILED"));

    assertThat(dsl.fetchValue(USERS.DISPLAY_NAME, USERS.ID.eq(me.id()))).isEqualTo("表示名");
    assertThat(dsl.fetchValue(USERS.ICON_KEY, USERS.ID.eq(me.id()))).isNull();
  }

  @Test
  void DBの保存に失敗すると_新しいアイコンを消し_古いアイコンは残す() throws Exception {
    Account me = accounts.signup();
    doAnswer(this::recordKey)
        .when(s3Client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    edit(me, TestImages.png()).andExpect(status().isOk());
    String old = storedKeys.getFirst();
    doThrow(new DataAccessResourceFailureException("DB の保存の失敗"))
        .when(userRepository)
        .updateProfile(anyLong(), any(), any(), any(), any());

    edit(me, TestImages.jpeg()).andExpect(status().isInternalServerError());

    assertThat(storedKeys).hasSize(2);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(exists(storedKeys.get(1))).as("新しいアイコン").isFalse());
    // 「起きないこと」は、決めた時間（1秒）待って確かめる（docs/test-plan.md 4.5）
    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(exists(old)).as("古いアイコン").isTrue());
    assertThat(dsl.fetchValue(USERS.ICON_KEY, USERS.ID.eq(me.id()))).isEqualTo(old);
  }

  // ===== 補助 =====

  private ResultActions edit(Account account, byte[] icon) throws Exception {
    return mockMvc.perform(
        multipart(HttpMethod.PUT, "/api/users/me")
            .file(
                new MockMultipartFile(
                    "data",
                    "",
                    MediaType.APPLICATION_JSON_VALUE,
                    objectMapper.writeValueAsBytes(Map.of("displayName", "山田"))))
            .file(new MockMultipartFile("icon", "icon.png", "image/png", icon))
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  /** 本物の S3（LocalStack）に保存し、保存したキーを覚えておく */
  private Object recordKey(InvocationOnMock invocation) throws Throwable {
    Object result = invocation.callRealMethod();
    storedKeys.add(invocation.getArgument(0, PutObjectRequest.class).key());
    return result;
  }

  private boolean exists(String key) {
    try {
      s3Client.headObject(r -> r.bucket(TestcontainersConfiguration.BUCKET).key(key));
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }
}
