package com.okimoto.fitlog.workout;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.support.MutableClock;
import com.okimoto.fitlog.support.TestAccounts;
import com.okimoto.fitlog.support.TestAccounts.Account;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * 実施日の「未来」は日本時間で決まる（docs/test-plan.md 4.4）。日本時間 11-14 23:59:59 と 11-15 00:00:00 に、実施日 11-15 を送る。
 *
 * <p>時計を差し替えるので、ほかの結合テストとは別の Spring の文脈で動く
 */
@Import({TestcontainersConfiguration.class, WorkoutDateBoundaryIntegrationTest.Clocks.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutDateBoundaryIntegrationTest {

  private static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-11-14T14:59:59Z"));

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DSLContext dsl;

  @TestConfiguration
  static class Clocks {
    @Bean
    @Primary
    MutableClock testClock() {
      return CLOCK;
    }
  }

  @Test
  void 日本時間の今日の終わりまでは明日の日付を送れず_0時になったら送れる() throws Exception {
    // 日本時間 2026-11-14 23:59:59。アクセストークン（15分）もこの時計で作るので、時計を合わせてから登録する
    CLOCK.set(Instant.parse("2026-11-14T14:59:59Z"));
    Account me = new TestAccounts(mockMvc, objectMapper, dsl).signup();
    create(me, "2026-11-15")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("performedOn"));
    create(me, "2026-11-14").andExpect(status().isCreated());

    // 日本時間 2026-11-15 00:00:00
    CLOCK.set(Instant.parse("2026-11-14T15:00:00Z"));
    create(me, "2026-11-15").andExpect(status().isCreated());
  }

  private ResultActions create(Account account, String performedOn) throws Exception {
    Map<String, Object> data =
        Map.of(
            "performedOn", performedOn,
            "visibility", "public",
            "weightUnit", "kg",
            "distanceUnit", "km",
            "exercises", List.of(Map.of("exerciseId", 8, "sets", List.of(Map.of("reps", 20)))));
    MockMultipartFile part =
        new MockMultipartFile(
            "data", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(data));
    return mockMvc.perform(
        multipart("/api/workouts").file(part).header(HttpHeaders.AUTHORIZATION, account.bearer()));
  }
}
