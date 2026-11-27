package com.okimoto.fitlog.workout.calc;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.workout.calc.PersonalBestRules.Entry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 自己ベスト更新の印を付ける種目とセット（docs/test-plan.md 5.3 のケース 4・12・13・14 のうち、SQL のあとで Java が選ぶ部分）。
 * どの記録・種目が更新したかは SQL が決める（PersonalBestRepositoryTest）
 */
class PersonalBestRulesTest {

  /** 種目の中で、自己ベストを出したセット */
  @Nested
  class 印を付けるセット {

    @Test
    void 一番重いセット() {
      assertThat(PersonalBestRules.bestSet(List.of(wr("60", 10), wr("70", 5), wr("65", 8))))
          .isEqualTo(1);
    }

    @Test
    void 同じ重さなら回数が多いセット() {
      assertThat(PersonalBestRules.bestSet(List.of(wr("70", 3), wr("70", 5), wr("70", 4))))
          .isEqualTo(1);
    }

    @Test
    void 同じ重さと回数なら並び順が先のセット() {
      assertThat(PersonalBestRules.bestSet(List.of(wr("60", 10), wr("70", 5), wr("70", 5))))
          .isEqualTo(1);
    }

    /** ケース 4：0回のセットは数えない */
    @Test
    void 回数が0回のセットは_重くても選ばない() {
      assertThat(PersonalBestRules.bestSet(List.of(wr("80", 0), wr("55", 5)))).isEqualTo(1);
    }

    @Test
    void 回数が1回のセットは選ぶ() {
      assertThat(PersonalBestRules.bestSet(List.of(wr("55", 5), wr("80", 1)))).isEqualTo(1);
    }

    @Test
    void 全セットが0回なら_なし() {
      assertThat(PersonalBestRules.bestSet(List.of(wr("80", 0), wr("60", 0)))).isEqualTo(-1);
    }

    /** ケース 14：回数のみ・時間と距離の種目は重量がない */
    @Test
    void 重量のないセットだけなら_なし() {
      assertThat(
              PersonalBestRules.bestSet(
                  List.of(
                      new StoredSet(null, 20, null, null),
                      new StoredSet(null, null, 1800, new BigDecimal("5.000")))))
          .isEqualTo(-1);
    }

    @Test
    void セットがなければ_なし() {
      assertThat(PersonalBestRules.bestSet(List.of())).isEqualTo(-1);
    }
  }

  /** 記録の中で、印を付ける種目（並び順の位置） */
  @Nested
  class 印を付ける種目 {

    @Test
    void 更新した種目に付ける() {
      assertThat(
              PersonalBestRules.markedExercises(
                  5, List.of(entry(1, "60"), entry(2, "100")), List.of(pb(5, 2, "100"))))
          .containsExactly(1);
    }

    @Test
    void 更新した種目がなければ_付けない() {
      assertThat(PersonalBestRules.markedExercises(5, List.of(entry(1, "60")), List.of()))
          .isEmpty();
    }

    @Test
    void ほかの記録の更新は_見ない() {
      assertThat(
              PersonalBestRules.markedExercises(
                  5, List.of(entry(1, "60")), List.of(pb(6, 1, "60"))))
          .isEmpty();
    }

    @Test
    void 更新した種目が2つなら_両方に付ける() {
      assertThat(
              PersonalBestRules.markedExercises(
                  5,
                  List.of(entry(1, "60"), entry(3, "20"), entry(2, "100")),
                  List.of(pb(5, 1, "60"), pb(5, 2, "100"))))
          .containsExactlyInAnyOrder(0, 2);
    }

    /** ケース 12：同じ種目が2回あれば、最大重量を出した方 */
    @Test
    void 同じ種目が2回なら_最大重量を出した方() {
      assertThat(
              PersonalBestRules.markedExercises(
                  5, List.of(entry(1, "65"), entry(1, "70")), List.of(pb(5, 1, "70"))))
          .containsExactly(1);
    }

    /** ケース 13：同じ重さなら並び順が前の方 */
    @Test
    void 同じ種目が2回で同じ重さなら_並び順が前の方() {
      assertThat(
              PersonalBestRules.markedExercises(
                  5, List.of(entry(1, "70"), entry(1, "70")), List.of(pb(5, 1, "70"))))
          .containsExactly(0);
    }

    @Test
    void 回数が1回以上のセットがない種目は_同じ種目でも選ばない() {
      assertThat(
              PersonalBestRules.markedExercises(
                  5, List.of(new Entry(1, null), entry(1, "70")), List.of(pb(5, 1, "70"))))
          .containsExactly(1);
    }

    /** DB の値（小数第3位まで）と、桁の違う値を比べても同じとみなす */
    @Test
    void 重さは桁の違いを見ない() {
      assertThat(
              PersonalBestRules.markedExercises(
                  5,
                  List.of(new Entry(1, new BigDecimal("70"))),
                  List.of(new PersonalBest(5, 1, new BigDecimal("70.000")))))
          .containsExactly(0);
    }

    /** 投稿カードは先頭5種目だけなので、最大重量を出した種目が入っていないことがある */
    @Test
    void 最大重量を出した種目がなければ_付けない() {
      assertThat(
              PersonalBestRules.markedExercises(
                  5, List.of(entry(1, "65")), List.of(pb(5, 1, "70"))))
          .isEmpty();
    }
  }

  /** 比べる値（種目の、回数が1回以上のセットの最大重量） */
  @Nested
  class 比べる値 {

    @Test
    void 詳細のセットからは_自己ベストを出したセットの重量() {
      assertThat(PersonalBestRules.entryOf(1, List.of(wr("60", 10), wr("80", 0), wr("70", 5))))
          .isEqualTo(new Entry(1, new BigDecimal("70.000")));
    }

    @Test
    void 詳細のセットに回数が1回以上のセットがなければ_null() {
      assertThat(PersonalBestRules.entryOf(1, List.of(wr("80", 0)))).isEqualTo(new Entry(1, null));
    }

    @Test
    void 投稿カードの集計からは_最大のセットの重量() {
      assertThat(PersonalBestRules.entryOf(aggregate("70.000", 5)))
          .isEqualTo(new Entry(1, new BigDecimal("70.000")));
    }

    /** 全セットが0回なら、集計の「最大のセット」は0回のセットになる（ExerciseAggregate）。0回のセットは数えない */
    @Test
    void 投稿カードの最大のセットが0回なら_null() {
      assertThat(PersonalBestRules.entryOf(aggregate("80.000", 0))).isEqualTo(new Entry(1, null));
    }

    @Test
    void 投稿カードの重量のない種目は_null() {
      assertThat(
              PersonalBestRules.entryOf(
                  new ExerciseAggregate(8, "腕立て伏せ", "reps", 1, true, null, 20, 20, null, null)))
          .isEqualTo(new Entry(8, null));
      assertThat(
              PersonalBestRules.entryOf(
                  new ExerciseAggregate(
                      48,
                      "ランニング",
                      "time_distance",
                      1,
                      false,
                      null,
                      null,
                      null,
                      1800,
                      new BigDecimal("5.000"))))
          .isEqualTo(new Entry(48, null));
    }

    private static ExerciseAggregate aggregate(String kg, int reps) {
      return new ExerciseAggregate(
          1, "ベンチプレス", "weight_reps", 1, true, new BigDecimal(kg), reps, reps, null, null);
    }
  }

  private static StoredSet wr(String kg, int reps) {
    return new StoredSet(new BigDecimal(kg).setScale(3), reps, null, null);
  }

  private static Entry entry(long exerciseId, String kg) {
    return new Entry(exerciseId, new BigDecimal(kg).setScale(3));
  }

  private static PersonalBest pb(long workoutId, long exerciseId, String kg) {
    return new PersonalBest(workoutId, exerciseId, new BigDecimal(kg).setScale(3));
  }
}
