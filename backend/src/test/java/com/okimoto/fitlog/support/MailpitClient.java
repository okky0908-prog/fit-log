package com.okimoto.fitlog.support;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * テスト用の Mailpit に届いたメールを、Mailpit の API で読む（docs/test-plan.md 3章「メール」）。
 *
 * <p>コンテナはテスト全体で共有するので、ほかのテストのメールと混ざらないよう、宛先（テストごとに違うアドレス）で絞って読む。中身を空にする方法は使わない（別のスレッドで送っている途中のメールを、ほかのテストから消してしまうため）
 */
public final class MailpitClient {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private MailpitClient() {}

  /** 宛先に届いたメール（新しい順） */
  public static List<Mail> messagesTo(String email) {
    JsonNode search =
        get(
            "/api/v1/search?query="
                + URLEncoder.encode("to:\"" + email + "\"", StandardCharsets.UTF_8));
    List<Mail> mails = new ArrayList<>();
    for (JsonNode summary : search.path("messages")) {
      JsonNode message = get("/api/v1/message/" + summary.path("ID").asString());
      mails.add(new Mail(message.path("Subject").asString(), message.path("Text").asString()));
    }
    return mails;
  }

  private static JsonNode get(String path) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(TestcontainersConfiguration.mailpitApiUrl() + path))
            .build();
    try {
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      return JSON.readTree(response.body());
    } catch (IOException e) {
      throw new IllegalStateException("Mailpit の API を呼べません", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Mailpit の API を待つ間に止められました", e);
    }
  }

  /** 届いたメール */
  public record Mail(String subject, String text) {}
}
