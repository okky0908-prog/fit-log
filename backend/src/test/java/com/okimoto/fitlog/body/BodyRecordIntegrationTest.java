package com.okimoto.fitlog.body;

import static com.okimoto.fitlog.jooq.Tables.BODY_RECORDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.support.MutableClock;
import com.okimoto.fitlog.support.SqlCounter;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 体重の記録（A-100〜A-103）を、API から DB まで通して確かめる（docs/api.md「体重」、docs/test-plan.md 4.4・4.6・4.7・5.x）。
 *
 * <p>「今日」と未来の日付の境界を確かめるため、時計を差し替える（ほかの結合テストとは別の Spring の文脈で動く）。アクセストークンもこの時計で作るので、時計を合わせてから登録する
 */
@Import({TestcontainersConfiguration.class, BodyRecordIntegrationTest.Clocks.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BodyRecordIntegrationTest {

  /** 日本時間 2026-11-14（土）12:00 */
  private static final Instant SATURDAY = Instant.parse("2026-11-14T03:00:00Z");

  private static final MutableClock CLOCK = new MutableClock(SATURDAY);

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;
  @Autowired private SqlCounter sqlCounter;

  private TestAccounts accounts;

  @TestConfiguration
  static class Clocks {
    @Bean
    @Primary
    MutableClock testClock() {
      return CLOCK;
    }
  }

  @BeforeEach
  void setUp() {
    CLOCK.set(SATURDAY);
    accounts = new TestAccounts(mockMvc, objectMapper, dsl);
  }

  @Nested
  class 記録_A100 {

    @Test
    void その日の記録がなければ201で作り_あれば200で上書きする() throws Exception {
      Account me = accounts.signup();

      record(me, "2026-11-14", "{\"weight\": 73.0, \"weightUnit\": \"kg\", \"bodyFatPct\": 18.5}")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.recordedOn").value("2026-11-14"))
          .andExpect(jsonPath("$.weightKg").value(73.0))
          .andExpect(jsonPath("$.bodyFatPct").value(18.5));
      // 150.0lb ＝ 68.0388555kg → 68.039kg。体脂肪率を入れなければ消える
      record(me, "2026-11-14", "{\"weight\": 150.0, \"weightUnit\": \"lb\"}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.weightKg").value(68.039))
          .andExpect(jsonPath("$.bodyFatPct").value(nullValue()));

      assertThat(dsl.fetchCount(BODY_RECORDS, BODY_RECORDS.USER_ID.eq(me.id()))).isEqualTo(1);
    }

    @Test
    void 範囲と刻みに合わなければ400で_すべての項目のエラーを返す() throws Exception {
      Account me = accounts.signup();

      record(me, "2026-11-14", "{\"weight\": 300.1, \"weightUnit\": \"kg\", \"bodyFatPct\": 75.1}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
          .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("weight", "bodyFatPct")));
      record(me, "2026-11-14", "{\"weight\": 73.0, \"weightUnit\": \"st\"}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("weightUnit")));
      assertThat(dsl.fetchCount(BODY_RECORDS, BODY_RECORDS.USER_ID.eq(me.id()))).isZero();
    }

    /** docs/test-plan.md 4.4。日本時間の 0時で「今日」が変わる */
    @Test
    void 未来の日付は400_日本時間の0時からはその日を記録できる() throws Exception {
      // 日本時間 2026-11-14 23:59:59
      CLOCK.set(Instant.parse("2026-11-14T14:59:59Z"));
      Account me = accounts.signup();
      String body = "{\"weight\": 73.0, \"weightUnit\": \"kg\"}";

      record(me, "2026-11-15", body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("recordedOn"));

      CLOCK.set(Instant.parse("2026-11-14T15:00:00Z"));
      record(me, "2026-11-15", body).andExpect(status().isCreated());
    }

    @Test
    void 日付と本文の形が違えば400_ログインしていなければ401() throws Exception {
      Account me = accounts.signup();
      record(me, "2026-13-01", "{\"weight\": 73.0, \"weightUnit\": \"kg\"}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      record(me, "2026-11-14", "{\"weight\": \"重い\", \"weightUnit\": \"kg\"}")
          .andExpect(status().isBadRequest());
      mockMvc
          .perform(
              put("/api/body-records/2026-11-14")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"weight\": 73.0, \"weightUnit\": \"kg\"}"))
          .andExpect(status().isUnauthorized());
    }

    /** docs/test-plan.md 4.6。2つのスレッドから同時に呼んでも、行は1件 */
    @Test
    void 同じ日を同時に2回記録しても1件() throws Exception {
      Account me = accounts.signup();
      CountDownLatch start = new CountDownLatch(1);
      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        List<Future<Integer>> results =
            List.of("73.0", "73.2").stream()
                .map(weight ->
                    executor.submit(() -> {
                      start.await();
                      return record(
                              me,
                              "2026-11-14",
                              "{\"weight\": " + weight + ", \"weightUnit\": \"kg\"}")
                          .andReturn()
                          .getResponse()
                          .getStatus();
                    }))
                .toList();
        start.countDown();

        assertThat(List.of(results.get(0).get(), results.get(1).get()))
            .containsExactlyInAnyOrder(201, 200);
      }
      assertThat(dsl.fetchCount(BODY_RECORDS, BODY_RECORDS.USER_ID.eq(me.id()))).isEqualTo(1);
    }
  }

  @Nested
  class 一覧_A101 {

    /** docs/test-plan.md 5.x の件数の境界（30件） */
    @Test
    void 日付の新しい順に30件ずつ_31件目は続きで返す() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      saveDays(me, 31);
      saveDays(sato, 1);

      String next =
          list(me, "")
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.items", hasSize(30)))
              .andExpect(jsonPath("$.items[0].recordedOn").value("2026-11-14"))
              .andExpect(jsonPath("$.items[29].recordedOn").value("2026-10-16"))
              .andExpect(jsonPath("$.hasNext").value(true))
              .andReturn()
              .getResponse()
              .getContentAsString();
      String cursor = objectMapper.readTree(next).get("nextCursor").asString();

      list(me, "cursor=" + cursor)
          .andExpect(jsonPath("$.items", hasSize(1)))
          .andExpect(jsonPath("$.items[0].recordedOn").value("2026-10-15"))
          .andExpect(jsonPath("$.hasNext").value(false))
          .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void ちょうど30件なら続きはない() throws Exception {
      Account me = accounts.signup();
      saveDays(me, 30);

      list(me, "")
          .andExpect(jsonPath("$.items", hasSize(30)))
          .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void カーソルの形が違えば400_ログインしていなければ401() throws Exception {
      Account me = accounts.signup();
      list(me, "cursor=abc$")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors[0].field").value("cursor"));
      mockMvc.perform(get("/api/body-records")).andExpect(status().isUnauthorized());
    }

    /** 1件のときと30件のときで、SQL の数が同じ（docs/test-plan.md 4.7） */
    @Test
    void 件数によらずSQLの数が同じ() throws Exception {
      Account me = accounts.signup();
      saveDays(me, 1);
      int one = sqlCounter.count(() -> list(me, "").andExpect(jsonPath("$.items", hasSize(1))));

      dsl.deleteFrom(BODY_RECORDS).where(BODY_RECORDS.USER_ID.eq(me.id())).execute();
      saveDays(me, 30);

      assertThat(one).isPositive();
      assertThat(sqlCounter.count(() -> list(me, "").andExpect(jsonPath("$.items", hasSize(30)))))
          .isEqualTo(one);
    }
  }

  @Nested
  class 削除_A102 {

    @Test
    void その日の記録を消して204_なければ404_他人の同じ日の記録は残る() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      record(me, "2026-11-14", "{\"weight\": 73.0, \"weightUnit\": \"kg\"}");
      record(sato, "2026-11-14", "{\"weight\": 60.0, \"weightUnit\": \"kg\"}");

      remove(me, "2026-11-14").andExpect(status().isNoContent());
      remove(me, "2026-11-14")
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("BODY_RECORD_NOT_FOUND"));
      assertThat(dsl.fetchCount(BODY_RECORDS, BODY_RECORDS.USER_ID.eq(sato.id()))).isEqualTo(1);
      mockMvc.perform(delete("/api/body-records/2026-11-14")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  class 推移_A103 {

    @Test
    void 期間がなければ直近3か月_日付の古い順_他人の記録は入らない() throws Exception {
      Account me = accounts.signup();
      Account sato = accounts.signup();
      // 2026-11-14 の3か月前は 2026-08-14。その日は入り、前の日は入らない
      record(me, "2026-08-13", "{\"weight\": 75.0, \"weightUnit\": \"kg\"}");
      record(me, "2026-11-01", "{\"weight\": 73.4, \"weightUnit\": \"kg\", \"bodyFatPct\": 18.7}");
      record(me, "2026-08-14", "{\"weight\": 74.0, \"weightUnit\": \"kg\"}");
      record(sato, "2026-10-01", "{\"weight\": 60.0, \"weightUnit\": \"kg\"}");

      trend(me, "")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.points", hasSize(2)))
          .andExpect(jsonPath("$.points[0].date").value("2026-08-14"))
          .andExpect(jsonPath("$.points[0].weightKg").value(74.0))
          .andExpect(jsonPath("$.points[0].bodyFatPct").value(nullValue()))
          .andExpect(jsonPath("$.points[1].date").value("2026-11-01"))
          .andExpect(jsonPath("$.points[1].bodyFatPct").value(18.7));
      trend(me, "range=1y").andExpect(jsonPath("$.points", hasSize(3)));
      trend(me, "range=3m").andExpect(jsonPath("$.points", hasSize(2)));
    }

    @Test
    void 期間が正しくなければ400_ログインしていなければ401() throws Exception {
      Account me = accounts.signup();
      for (String range : new String[] {"all", "6m", "1Y"}) {
        trend(me, "range=" + range)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
      }
      mockMvc.perform(get("/api/body-records/trend")).andExpect(status().isUnauthorized());
    }
  }

  /** 今日（2026-11-14）から count 日分さかのぼって記録する */
  private void saveDays(Account account, int count) throws Exception {
    LocalDate today = LocalDate.parse("2026-11-14");
    for (int i = 0; i < count; i++) {
      record(account, today.minusDays(i).toString(), "{\"weight\": 73.0, \"weightUnit\": \"kg\"}")
          .andExpect(status().isCreated());
    }
  }

  private ResultActions record(Account account, String date, String body) throws Exception {
    return mockMvc.perform(
        put("/api/body-records/" + date)
            .header(HttpHeaders.AUTHORIZATION, account.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private ResultActions list(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/body-records?" + query).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions remove(Account account, String date) throws Exception {
    return mockMvc.perform(
        delete("/api/body-records/" + date).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }

  private ResultActions trend(Account account, String query) throws Exception {
    return mockMvc.perform(
        get("/api/body-records/trend?" + query)
            .header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }
}
