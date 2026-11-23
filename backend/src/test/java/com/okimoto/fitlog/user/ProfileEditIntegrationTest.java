package com.okimoto.fitlog.user;

import static com.okimoto.fitlog.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.image.calc.ImageRules;
import com.okimoto.fitlog.notification.repository.NewNotification;
import com.okimoto.fitlog.notification.repository.NotificationRepository;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestImages;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.user.calc.ProfileRules;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** プロフィールの編集（A-82）と、ほかの API のアイコンの URL（docs/api.md A-82・UserSummary、docs/test-plan.md 5.1・5.2） */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileEditIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private S3Client s3Client;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private NotificationRepository notificationRepository;

  private TestAccounts accounts;

  @BeforeEach
  void setUp() {
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
  }

  @Test
  void 表示名と自己紹介を整えて保存し_プロフィールの形で返す() throws Exception {
    Account me = accounts.signup();

    edit(me, data(" 山田太郎 ", "\r\n週4で\r\n筋トレしています\r\n"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(me.username()))
        .andExpect(jsonPath("$.displayName").value("山田太郎"))
        .andExpect(jsonPath("$.bio").value("週4で\n筋トレしています"))
        .andExpect(jsonPath("$.iconUrl").isEmpty())
        .andExpect(jsonPath("$.me").value(true))
        .andExpect(jsonPath("$.followStatus").value("none"))
        .andExpect(jsonPath("$.canViewRecords").value(true));

    mockMvc
        .perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, me.bearer()))
        .andExpect(jsonPath("$.displayName").value("山田太郎"))
        .andExpect(jsonPath("$.bio").value("週4で\n筋トレしています"));
  }

  @Test
  void 自己紹介を空にするとnullになる() throws Exception {
    Account me = accounts.signup();
    edit(me, data("山田", "自己紹介"));

    edit(me, data("山田", "  ")).andExpect(jsonPath("$.bio").isEmpty());

    assertThat(dsl.fetchValue(USERS.BIO, USERS.ID.eq(me.id()))).isNull();
  }

  @Test
  void 入力に誤りがあれば400で_項目ごとの誤りを返し_何も変えない() throws Exception {
    Account me = accounts.signup();

    edit(me, data("", "あ".repeat(161)), TestImages.gif())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[0].field").value("displayName"))
        .andExpect(jsonPath("$.errors[0].message").value(ProfileRules.DISPLAY_NAME_MESSAGE))
        .andExpect(jsonPath("$.errors[1].field").value("bio"))
        .andExpect(jsonPath("$.errors[2].field").value("icon"))
        .andExpect(jsonPath("$.errors[2].message").value(ImageRules.TYPE_MESSAGE));

    assertThat(dsl.fetchValue(USERS.DISPLAY_NAME, USERS.ID.eq(me.id()))).isEqualTo("表示名");
    assertThat(iconKey(me)).isNull();
  }

  @Test
  void 表示名が51文字なら400_絵文字50個と自己紹介160文字は保存できる() throws Exception {
    Account me = accounts.signup();

    edit(me, data("あ".repeat(51), null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("displayName"));
    edit(me, data("💪".repeat(50), "あ".repeat(160))).andExpect(status().isOk());
  }

  @Test
  void dataのパートがなければ400() throws Exception {
    Account me = accounts.signup();

    mockMvc
        .perform(
            multipart(HttpMethod.PUT, "/api/users/me")
                .file(new MockMultipartFile("icon", "icon.png", "image/png", TestImages.png()))
                .header(HttpHeaders.AUTHORIZATION, me.bearer()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void アイコンを送ると保存して署名付きURLを返し_送らなければそのまま() throws Exception {
    Account me = accounts.signup();
    byte[] icon = TestImages.webp();

    JsonNode saved = json(edit(me, data("山田", null), icon).andExpect(status().isOk()));

    String key = iconKey(me);
    assertThat(key).startsWith("icons/").endsWith(".webp");
    assertThat(download(saved.get("iconUrl").asString())).isEqualTo(icon);

    edit(me, data("山田花子", null)).andExpect(jsonPath("$.iconUrl").isNotEmpty());
    assertThat(iconKey(me)).isEqualTo(key);
    assertThat(exists(key)).isTrue();
  }

  @Test
  void アイコンを差し替えると_古いアイコンは保存の確定後に消す() throws Exception {
    Account me = accounts.signup();
    edit(me, data("山田", null), TestImages.png());
    String old = iconKey(me);

    edit(me, data("山田", null), TestImages.jpeg()).andExpect(status().isOk());

    String current = iconKey(me);
    assertThat(current).isNotEqualTo(old).endsWith(".jpg");
    assertThat(exists(current)).isTrue();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(exists(old)).as("古いアイコン").isFalse());
  }

  @Test
  void 位置情報を含むアイコン_2MBを超えるアイコンは400() throws Exception {
    Account me = accounts.signup();

    edit(me, data("山田", null), TestImages.jpegWithGps())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("icon"))
        .andExpect(jsonPath("$.errors[0].message").value(ImageRules.LOCATION_MESSAGE));
    edit(me, data("山田", null), TestImages.jpegOfSize(ImageRules.MAX_BYTES + 1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("icon"));
    edit(me, data("山田", null), TestImages.jpegOfSize(ImageRules.MAX_BYTES))
        .andExpect(status().isOk());
  }

  @Test
  void ログインしていなければ401() throws Exception {
    mockMvc
        .perform(multipart(HttpMethod.PUT, "/api/users/me").file(dataPart(data("山田", null))))
        .andExpect(status().isUnauthorized());
  }

  /** これまで null を返していた iconUrl に、アイコンの署名付き URL を返す（docs/api.md「UserSummary」） */
  @Test
  void アイコンを設定すると_ほかのAPIのアイコンのURLにも出る() throws Exception {
    Account sato = accounts.signup();
    Account me = accounts.signup();
    byte[] icon = TestImages.png();
    edit(sato, data("佐藤", null), icon);
    long workout = new WorkoutFixture(dsl, workoutRepository).of(sato.id()).save();
    notificationRepository.insert(
        new NewNotification(me.id(), sato.id(), "follow", null, OffsetDateTime.now()));
    mockMvc.perform(
        post("/api/users/" + sato.username() + "/follow")
            .header(HttpHeaders.AUTHORIZATION, me.bearer()));

    for (String url :
        new String[] {
          url(me, "/api/workouts/" + workout, "$.author.iconUrl"),
          url(me, "/api/notifications", "$.items[0].actor.iconUrl"),
          url(me, "/api/users/" + me.username() + "/following", "$.items[0].iconUrl"),
          url(me, "/api/users/" + sato.username(), "$.iconUrl"),
          url(sato, "/api/auth/me", "$.iconUrl"),
        }) {
      assertThat(download(url)).isEqualTo(icon);
    }
  }

  // ===== 補助 =====

  private static Map<String, Object> data(String displayName, String bio) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("displayName", displayName);
    data.put("bio", bio);
    return data;
  }

  private MockMultipartFile dataPart(Map<String, Object> data) throws Exception {
    return new MockMultipartFile(
        "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(data));
  }

  private ResultActions edit(Account account, Map<String, Object> data, byte[]... icons)
      throws Exception {
    var request = multipart(HttpMethod.PUT, "/api/users/me").file(dataPart(data));
    for (byte[] icon : icons) {
      // ファイル名と Content-Type は、わざと中身と合わない値にする（中身で判定することを確かめる）
      request.file(new MockMultipartFile("icon", "icon.png", "image/png", icon));
    }
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  /** その API の応答の、JSON のパスの値（アイコンの URL） */
  private String url(Account account, String path, String jsonPath) throws Exception {
    JsonNode body =
        json(
            mockMvc
                .perform(get(path).header(HttpHeaders.AUTHORIZATION, account.bearer()))
                .andExpect(status().isOk()));
    JsonNode node = body;
    for (String part : jsonPath.substring(2).split("\\.")) {
      if (part.endsWith("[0]")) {
        node = node.get(part.substring(0, part.length() - 3)).get(0);
      } else {
        node = node.get(part);
      }
    }
    assertThat(node.isString()).as("%s の %s", path, jsonPath).isTrue();
    return node.asString();
  }

  private String iconKey(Account account) {
    return dsl.fetchValue(USERS.ICON_KEY, USERS.ID.eq(account.id()));
  }

  private boolean exists(String key) {
    try {
      s3Client.headObject(r -> r.bucket(TestcontainersConfiguration.BUCKET).key(key));
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    }
  }

  private JsonNode json(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  /** ブラウザと同じく、署名付き URL に GET する */
  private static byte[] download(String url) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<byte[]> response =
          client.send(
              HttpRequest.newBuilder(URI.create(url)).GET().build(),
              HttpResponse.BodyHandlers.ofByteArray());
      assertThat(response.statusCode()).as("署名付き URL の応答").isEqualTo(200);
      return response.body();
    }
  }
}
