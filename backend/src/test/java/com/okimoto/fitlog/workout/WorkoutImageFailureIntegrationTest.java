package com.okimoto.fitlog.workout;

import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestImages;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
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
 * 画像の保存の途中で S3・DB が失敗したときの後片付け（docs/api.md A-20、docs/test-plan.md 5.2 のケース 7・8、4.5）。
 *
 * <p>S3 のクライアントと Repository
 * を、本物を包んだ偽物（spy）にして、決めた所で失敗させる。保存した画像を消す処理は、取り消しのあとに別のスレッドで動くので、Awaitility で待つ
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutImageFailureIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private Clock clock;
  @MockitoSpyBean private S3Client s3Client;
  @MockitoSpyBean private WorkoutRepository workoutRepository;

  /** S3 に保存したキー（失敗したものは入らない） */
  private final List<String> storedKeys = new CopyOnWriteArrayList<>();

  private TestAccounts accounts;

  @BeforeEach
  void setUp() {
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
    Mockito.reset(s3Client, workoutRepository);
    storedKeys.clear();
  }

  @Test
  void 二枚目の保存に失敗すると500で記録を作らず_一枚目は消す() throws Exception {
    Account me = accounts.signup();
    AtomicInteger calls = new AtomicInteger();
    doAnswer(
            invocation -> {
              if (calls.incrementAndGet() == 2) {
                throw SdkClientException.create("S3 につながらない");
              }
              return recordKey(invocation);
            })
        .when(s3Client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    create(me, TestImages.jpeg(), TestImages.png())
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_FAILED"))
        .andExpect(jsonPath("$.message").value("画像を保存できませんでした。時間をおいてもう一度お試しください"));

    assertThat(dsl.fetchCount(WORKOUTS, WORKOUTS.USER_ID.eq(me.id()))).isZero();
    assertThat(storedKeys).hasSize(1);
    awaitDeleted(storedKeys.getFirst());
  }

  @Test
  void DBの保存に失敗すると_保存した画像をすべて消す() throws Exception {
    Account me = accounts.signup();
    doAnswer(this::recordKey)
        .when(s3Client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));
    doThrow(new DataAccessResourceFailureException("DB の保存の失敗"))
        .when(workoutRepository)
        .insertImages(anyLong(), anyList(), any());

    create(me, TestImages.jpeg(), TestImages.png())
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

    assertThat(dsl.fetchCount(WORKOUTS, WORKOUTS.USER_ID.eq(me.id()))).isZero();
    assertThat(storedKeys).hasSize(2);
    storedKeys.forEach(this::awaitDeleted);
  }

  @Test
  void 保存が確定したら_画像は消さない() throws Exception {
    Account me = accounts.signup();
    doAnswer(this::recordKey)
        .when(s3Client)
        .putObject(any(PutObjectRequest.class), any(RequestBody.class));

    create(me, TestImages.jpeg()).andExpect(status().isCreated());

    // 「起きないこと」は、決めた時間（1秒）待って確かめる（docs/test-plan.md 4.5）
    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(exists(storedKeys.getFirst())).isTrue());
  }

  // ===== 補助 =====

  /** 本物の S3（LocalStack）に保存し、保存したキーを覚えておく */
  private Object recordKey(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
    Object result = invocation.callRealMethod();
    storedKeys.add(invocation.getArgument(0, PutObjectRequest.class).key());
    return result;
  }

  private void awaitDeleted(String key) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(exists(key)).as("S3 の %s", key).isFalse());
  }

  private boolean exists(String key) {
    try {
      s3Client.headObject(r -> r.bucket(TestcontainersConfiguration.BUCKET).key(key));
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  private ResultActions create(Account account, byte[]... images) throws Exception {
    Map<String, Object> data =
        Map.of(
            "performedOn",
            JapanTime.today(clock).toString(),
            "visibility",
            "public",
            "weightUnit",
            "kg",
            "distanceUnit",
            "km",
            "exercises",
            List.of(Map.of("exerciseId", 8L, "sets", List.of(Map.of("reps", 20)))));
    var request =
        multipart("/api/workouts")
            .file(
                new MockMultipartFile(
                    "data",
                    "",
                    MediaType.APPLICATION_JSON_VALUE,
                    objectMapper.writeValueAsBytes(data)));
    for (byte[] image : images) {
      request.file(new MockMultipartFile("images", "photo", "image/jpeg", image));
    }
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }
}
