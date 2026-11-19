package com.okimoto.fitlog.exercise.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.okimoto.fitlog.auth.service.JwtService;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 種目の検索（A-30）のテスト。
 *
 * <p>段階 0 では、最初から用意された種目の一覧だけを確かめる。ログイン・自分専用の種目・検索の条件（q・bodyPart）は段階 1 で足す。
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExerciseControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;

  /**
   * 最初から用意された58種目を、部位の順（胸・背中・脚・肩・腕・腹・全身・有酸素）→ 部位の中の順で返す。
   *
   * <p>確かめる要素は、並びの境目（最初・各記録方式・ランニング・最後）。部位ごとの件数は 胸8・背中7・脚9・肩7・腕6・腹5・全身5・有酸素11 なので、有酸素の最初（ランニング）は
   * 48番目
   */
  @Test
  void 最初から用意された種目を部位の順に返す() throws Exception {
    mockMvc
        .perform(
            get("/api/exercises")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issue(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(58))
        // 最初：胸の1番目。重量 × 回数
        .andExpect(jsonPath("$[0].id").value(1))
        .andExpect(jsonPath("$[0].name").value("ベンチプレス"))
        .andExpect(jsonPath("$[0].bodyPart").value("chest"))
        .andExpect(jsonPath("$[0].recordType").value("weight_reps"))
        .andExpect(jsonPath("$[0].isRunning").value(false))
        .andExpect(jsonPath("$[0].custom").value(false))
        // 胸の最後：回数のみ
        .andExpect(jsonPath("$[7].name").value("腕立て伏せ"))
        .andExpect(jsonPath("$[7].recordType").value("reps"))
        // 背中の最初（部位が変わる境目）
        .andExpect(jsonPath("$[8].name").value("デッドリフト"))
        .andExpect(jsonPath("$[8].bodyPart").value("back"))
        // 有酸素の最初：ランニングの集計の対象
        .andExpect(jsonPath("$[47].name").value("ランニング"))
        .andExpect(jsonPath("$[47].bodyPart").value("cardio"))
        .andExpect(jsonPath("$[47].recordType").value("time_distance"))
        .andExpect(jsonPath("$[47].isRunning").value(true))
        // 最後：有酸素の11番目
        .andExpect(jsonPath("$[57].name").value("縄跳び"))
        .andExpect(jsonPath("$[57].isRunning").value(false));
  }

  @Test
  void ログインしていなければ401を返す() throws Exception {
    mockMvc
        .perform(get("/api/exercises"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("ログインが必要です"))
        .andExpect(jsonPath("$.errors").isEmpty());
  }
}
