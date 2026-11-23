package com.okimoto.fitlog.workout;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestImages;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * multipart の大きさの上限（docs/test-plan.md 5.1 の「画像」「リクエスト全体の大きさ」。A-20・A-82）。
 *
 * <p>MockMvc は multipart の大きさの上限（application.yml の
 * spring.servlet.multipart）を確かめないので、本物のサーバー（空いているポート）を立てて HTTP で送る
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutUploadLimitIntegrationTest {

  private static final String BOUNDARY = "fitlog-test-boundary";
  private static final int TWO_MB = 2 * 1024 * 1024;

  @LocalServerPort private int port;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private Clock clock;

  private Account me;

  @BeforeEach
  void setUp() throws Exception {
    me = new TestAccounts(mockMvc, objectMapper, dsl).signup();
  }

  @Test
  void リクエスト全体が10MBを超えると413() throws Exception {
    // 2MB の画像6枚（12MB）
    HttpResponse<String> response = post(Collections.nCopies(6, TestImages.jpegOfSize(TWO_MB)));

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(json(response).get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
  }

  @Test
  void 二MBの画像4枚は_リクエスト全体が10MB以下なので作れる() throws Exception {
    HttpResponse<String> response = post(Collections.nCopies(4, TestImages.jpegOfSize(TWO_MB)));

    assertThat(response.statusCode()).isEqualTo(201);
  }

  @Test
  void 二MBを超える画像1枚は_413ではなく400で_どの画像かを返す() throws Exception {
    // 1つのファイルの上限を 2MB にすると、ここで 413 になり、画面がどの画像が悪いかを出せない（docs/api.md「エラー」）
    HttpResponse<String> response =
        post(List.of(TestImages.jpeg(), TestImages.jpegOfSize(TWO_MB + 1)));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(json(response).get("errors").get(0).get("field").asString()).isEqualTo("images[1]");
  }

  @Test
  void プロフィールの編集も_リクエスト全体が10MBを超えると413() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    part(out, "Content-Disposition: form-data; name=\"data\"\r\nContent-Type: application/json");
    out.writeBytes(objectMapper.writeValueAsBytes(Map.of("displayName", "山田")));
    out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    part(
        out,
        "Content-Disposition: form-data; name=\"icon\"; filename=\"icon.jpg\"\r\n"
            + "Content-Type: image/jpeg");
    out.writeBytes(TestImages.jpegOfSize(11 * 1024 * 1024));
    out.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

    HttpResponse<String> response;
    try (HttpClient client = HttpClient.newHttpClient()) {
      response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/users/me"))
                  .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                  .header("Authorization", me.bearer())
                  .PUT(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
    }

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(json(response).get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
  }

  private HttpResponse<String> post(List<byte[]> images) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/workouts"))
              .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
              .header("Authorization", me.bearer())
              .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(images)))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  private JsonNode json(HttpResponse<String> response) {
    return objectMapper.readTree(response.body());
  }

  /** 記録の JSON（data）と画像（images）を、ブラウザと同じ multipart/form-data の形にする */
  private byte[] multipart(List<byte[]> images) {
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
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    part(out, "Content-Disposition: form-data; name=\"data\"\r\nContent-Type: application/json");
    out.writeBytes(objectMapper.writeValueAsBytes(data));
    out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    for (int i = 0; i < images.size(); i++) {
      part(
          out,
          "Content-Disposition: form-data; name=\"images\"; filename=\""
              + i
              + ".jpg\"\r\nContent-Type: image/jpeg");
      out.writeBytes(images.get(i));
      out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    out.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }

  private static void part(ByteArrayOutputStream out, String headers) {
    out.writeBytes(
        ("--" + BOUNDARY + "\r\n" + headers + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
  }
}
