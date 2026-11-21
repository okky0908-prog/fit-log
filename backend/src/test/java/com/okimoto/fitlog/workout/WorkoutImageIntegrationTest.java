package com.okimoto.fitlog.workout;

import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_IMAGES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestImages;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 記録の画像（A-20・A-21）を、API から S3（LocalStack）まで通して確かめる（docs/api.md A-20、docs/test-plan.md 5.1・5.2）。 S3
 * や DB を失敗させるケースは {@link WorkoutImageFailureIntegrationTest}
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutImageIntegrationTest {

  private static final String KEY_PATTERN = "workouts/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private S3Client s3Client;
  @Autowired private Clock clock;

  private TestAccounts accounts;

  @BeforeEach
  void setUp() {
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
  }

  @Test
  void 画像つきで作ると_S3に保存し_選んだ順に署名付きURLを返す_URLで画像を取れる() throws Exception {
    Account me = accounts.signup();
    byte[] jpeg = TestImages.jpeg();
    byte[] png = TestImages.png();
    byte[] webp = TestImages.webp();

    String body =
        create(me, valid(), webp, jpeg, png)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.images.length()").value(3))
            .andExpect(jsonPath("$.images[0].sortOrder").value(1))
            .andExpect(jsonPath("$.images[1].sortOrder").value(2))
            .andExpect(jsonPath("$.images[2].sortOrder").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode images = objectMapper.readTree(body).get("images");
    long id = objectMapper.readTree(body).get("id").asLong();

    // DB には S3 のキーだけを、選んだ順に保存する。拡張子は中身の形式で決まる
    List<String> keys =
        dsl.select(WORKOUT_IMAGES.IMAGE_KEY)
            .from(WORKOUT_IMAGES)
            .where(WORKOUT_IMAGES.WORKOUT_ID.eq(id))
            .orderBy(WORKOUT_IMAGES.SORT_ORDER)
            .fetch(WORKOUT_IMAGES.IMAGE_KEY);
    assertThat(keys.get(0)).matches(KEY_PATTERN + "webp");
    assertThat(keys.get(1)).matches(KEY_PATTERN + "jpg");
    assertThat(keys.get(2)).matches(KEY_PATTERN + "png");
    // 年と月は日本時間
    String yearMonth = JapanTime.today(clock).toString().substring(0, 7).replace('-', '/');
    assertThat(keys).allMatch(key -> key.startsWith("workouts/" + yearMonth + "/"));
    assertThat(head(keys.get(1)).contentType()).isEqualTo("image/jpeg");

    // 署名付き URL で、保存した画像そのものを取れる（LocalStack に署名を確かめさせている）
    assertThat(images.get(0).get("id").asLong()).isPositive();
    assertThat(download(images.get(0).get("url").asString())).isEqualTo(webp);
    assertThat(download(images.get(1).get("url").asString())).isEqualTo(jpeg);
    assertThat(download(images.get(2).get("url").asString())).isEqualTo(png);
  }

  @Test
  void 署名を書き換えたURLでは_画像を取れない() throws Exception {
    // LocalStack が署名を確かめていることの確認（確かめていないと、上のテストが署名の誤りに気づけない）。
    // なお LocalStack はバケットの非公開（アクセス権）までは再現しないので、署名のない URL で取れないことは確かめられない
    Account me = accounts.signup();
    String body =
        create(me, valid(), TestImages.png()).andReturn().getResponse().getContentAsString();
    String url = objectMapper.readTree(body).get("images").get(0).get("url").asString();
    String tampered = url.substring(0, url.length() - 1) + (url.endsWith("0") ? "1" : "0");

    assertThat(statusOf(url)).isEqualTo(200);
    assertThat(statusOf(tampered)).isEqualTo(403);
  }

  @Test
  void 見てよい他人の記録の画像も_詳細で署名付きURLを返す() throws Exception {
    Account author = accounts.signup();
    long id = idOf(create(author, valid(), TestImages.png()));

    String body =
        detail(accounts.signup(), id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.images.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String url = objectMapper.readTree(body).get("images").get(0).get("url").asString();
    assertThat(download(url)).isEqualTo(TestImages.png());
  }

  @Test
  void 見てはいけない記録の画像のURLは_応答のどこにも出ない() throws Exception {
    Account author = accounts.signup();
    Map<String, Object> data = valid();
    data.put("visibility", "private");
    long id = idOf(create(author, data, TestImages.png()));

    detail(accounts.signup(), id)
        .andExpect(status().isNotFound())
        .andExpect(content().string(not(containsString("X-Amz"))))
        .andExpect(content().string(not(containsString("workouts/"))));
  }

  @Test
  void 画像がなければ空の配列() throws Exception {
    create(accounts.signup(), valid())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.images").isEmpty());
  }

  @Test
  void 画像は4枚まで_5枚は400で_記録を作らない() throws Exception {
    Account me = accounts.signup();
    byte[][] four = Collections.nCopies(4, TestImages.jpeg()).toArray(byte[][]::new);
    byte[][] five = Collections.nCopies(5, TestImages.jpeg()).toArray(byte[][]::new);

    create(me, valid(), four).andExpect(status().isCreated());
    create(me, valid(), five)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("images"))
        .andExpect(jsonPath("$.errors[0].message").value("画像は4枚まで選べます"));

    assertThat(dsl.fetchCount(WORKOUTS, WORKOUTS.USER_ID.eq(me.id()))).isEqualTo(1);
  }

  @Test
  void 大きさは1枚2MBちょうどまで() throws Exception {
    Account me = accounts.signup();

    create(me, valid(), TestImages.jpegOfSize(2 * 1024 * 1024)).andExpect(status().isCreated());
    create(me, valid(), TestImages.jpegOfSize(2 * 1024 * 1024 + 1))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("images[0]"))
        .andExpect(jsonPath("$.errors[0].message").value("画像は1枚2MBまでです"));
  }

  @Test
  void 形式と位置情報の違反は_どの画像かを返し_ほかの項目のエラーとまとめて返す() throws Exception {
    Account me = accounts.signup();
    Map<String, Object> data = valid();
    data.put("performedOn", JapanTime.today(clock).plusDays(1).toString());

    create(
            me,
            data,
            TestImages.jpeg(),
            TestImages.gif(),
            TestImages.jpegWithGps(),
            TestImages.webpWithXmpLocation())
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.errors[*].field")
                .value(containsInAnyOrder("performedOn", "images[1]", "images[2]", "images[3]")))
        .andExpect(
            jsonPath("$.errors[?(@.field == 'images[1]')].message")
                .value("JPEG・PNG・WebP の画像を選んでください"))
        .andExpect(
            jsonPath("$.errors[?(@.field == 'images[2]')].message").value("位置情報を含む画像はアップロードできません"));

    assertThat(dsl.fetchCount(WORKOUTS, WORKOUTS.USER_ID.eq(me.id()))).isZero();
  }

  @Test
  void 位置情報のないEXIFは受け付ける() throws Exception {
    create(accounts.signup(), valid(), TestImages.jpegWithExifWithoutGps())
        .andExpect(status().isCreated());
  }

  // ===== 補助 =====

  private ResultActions create(Account account, Map<String, Object> data, byte[]... images)
      throws Exception {
    var request =
        multipart("/api/workouts")
            .file(
                new MockMultipartFile(
                    "data",
                    "",
                    MediaType.APPLICATION_JSON_VALUE,
                    objectMapper.writeValueAsBytes(data)));
    for (byte[] image : images) {
      // ファイル名と Content-Type は、わざと中身と合わない値にする（中身で判定することを確かめる）
      request.file(new MockMultipartFile("images", "photo.png", "image/png", image));
    }
    return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions detail(Account account, long id) throws Exception {
    return mockMvc.perform(
        get("/api/workouts/" + id).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private long idOf(ResultActions created) throws Exception {
    String body =
        created.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  private Map<String, Object> valid() {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("performedOn", JapanTime.today(clock).toString());
    data.put("visibility", "public");
    data.put("weightUnit", "kg");
    data.put("distanceUnit", "km");
    data.put("exercises", List.of(Map.of("exerciseId", 8L, "sets", List.of(Map.of("reps", 20)))));
    return data;
  }

  private HeadObjectResponse head(String key) {
    return s3Client.headObject(r -> r.bucket(TestcontainersConfiguration.BUCKET).key(key));
  }

  private static int statusOf(String url) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client
          .send(
              HttpRequest.newBuilder(URI.create(url)).GET().build(),
              HttpResponse.BodyHandlers.discarding())
          .statusCode();
    }
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
