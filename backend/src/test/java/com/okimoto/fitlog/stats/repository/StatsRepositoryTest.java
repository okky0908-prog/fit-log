package com.okimoto.fitlog.stats.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.stats.calc.DayTotal;
import com.okimoto.fitlog.stats.calc.StatsRules.Period;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.calc.StoredSet;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 週間・月間の集計の SQL。本人の記録だけを、公開範囲に関係なく、期間の両端の日を含めて集計する（docs/test-plan.md 4.2）。
 *
 * <p>集計するのは同じ人の記録だけなので、テストごとに新しいユーザーを作る。テストごとにロールバックする
 */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  StatsRepository.class,
  WorkoutRepository.class,
  UserRepository.class
})
@ActiveProfiles("test")
class StatsRepositoryTest {

  /** 2026-11-09（月）〜 11-15（日） */
  private static final Period WEEK =
      new Period(LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-15"));

  @Autowired private StatsRepository repository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private WorkoutFixture workouts;
  private long me;

  @BeforeEach
  void setUp() {
    workouts = new WorkoutFixture(dsl, workoutRepository);
    me = user("stats_me");
  }

  @Nested
  class 日ごとの合計 {

    @Test
    void 同じ日の記録をまとめて_重量と回数_時間_距離を足す() {
      workouts
          .of(me)
          .performedOn(day("2026-11-10"))
          .weightReps("ベンチプレス", 60, 10, 3)
          .reps("腕立て伏せ", 20, 2)
          .save();
      workouts
          .of(me)
          .performedOn(day("2026-11-10"))
          .weightReps("スクワット", 100.5, 5, 1)
          .timeDistance("ランニング", 1800, 5.0)
          .timeDistance("プランク", 60, null)
          .save();

      // 60 × 10 × 3 + 100.5 × 5 = 2302.5。回数のみの種目は総挙上量に入らない。距離のないセットは距離に入らない
      assertThat(repository.dailyTotals(me, WEEK))
          .containsExactly(new DayTotal(day("2026-11-10"), kg("2302.500"), 1860, kg("5.000")));
    }

    @Test
    void 日付の順に_記録のある日だけを返す() {
      workouts.of(me).performedOn(day("2026-11-14")).weightReps("ベンチプレス", 50, 2, 1).save();
      workouts.of(me).performedOn(day("2026-11-11")).reps("腕立て伏せ", 20, 1).save();

      assertThat(repository.dailyTotals(me, WEEK))
          .containsExactly(
              new DayTotal(day("2026-11-11"), kg("0.000"), 0, kg("0.000")),
              new DayTotal(day("2026-11-14"), kg("100.000"), 0, kg("0.000")));
    }

    @Test
    void 回数が0回のセットは_総挙上量に入らない() {
      workouts
          .of(me)
          .performedOn(day("2026-11-10"))
          .exercise("ベンチプレス", List.of(wr("80", 0), wr("50", 10)))
          .save();

      assertThat(repository.dailyTotals(me, WEEK))
          .extracting(DayTotal::volumeKg)
          .containsExactly(kg("500.000"));
    }

    @Test
    void 期間の両端の日を含み_外の日は含まない() {
      for (String date : List.of("2026-11-08", "2026-11-09", "2026-11-15", "2026-11-16")) {
        workouts.of(me).performedOn(day(date)).weightReps("ベンチプレス", 10, 1, 1).save();
      }

      assertThat(repository.dailyTotals(me, WEEK))
          .extracting(DayTotal::date)
          .containsExactly(day("2026-11-09"), day("2026-11-15"));
    }

    @Test
    void 自分のみの記録も含め_他人の記録は含まない() {
      long sato = user("stats_sato");
      workouts
          .of(me)
          .performedOn(day("2026-11-10"))
          .visibility("private")
          .weightReps("ベンチプレス", 60, 10, 1)
          .save();
      workouts.of(sato).performedOn(day("2026-11-10")).weightReps("ベンチプレス", 100, 10, 1).save();
      workouts.of(sato).performedOn(day("2026-11-12")).weightReps("ベンチプレス", 100, 10, 1).save();

      assertThat(repository.dailyTotals(me, WEEK))
          .containsExactly(new DayTotal(day("2026-11-10"), kg("600.000"), 0, kg("0.000")));
    }

    @Test
    void 記録がなければ空() {
      assertThat(repository.dailyTotals(me, WEEK)).isEmpty();
    }
  }

  @Nested
  class 部位別のセット数 {

    @Test
    void セット数の多い順_同じなら部位の決まった順() {
      workouts
          .of(me)
          .performedOn(day("2026-11-10"))
          .weightReps("ベンチプレス", 60, 10, 3)
          .reps("腕立て伏せ", 20, 1)
          .timeDistance("ランニング", 1800, 5.0)
          .weightReps("スクワット", 100, 5, 1)
          .save();
      workouts
          .of(me)
          .performedOn(day("2026-11-12"))
          .visibility("private")
          .weightReps("デッドリフト", 120, 5, 2)
          .save();

      // 胸 4（ベンチプレス 3 と腕立て伏せ 1）、背中 2、脚と有酸素が 1 ずつ（決まった順で脚が先）
      assertThat(repository.bodyParts(me, WEEK))
          .containsExactly(
              new BodyPartCount("chest", 4),
              new BodyPartCount("back", 2),
              new BodyPartCount("legs", 1),
              new BodyPartCount("cardio", 1));
    }

    @Test
    void 期間の外と他人の記録は数えない() {
      long sato = user("stats_sato2");
      workouts.of(me).performedOn(day("2026-11-08")).weightReps("ベンチプレス", 60, 10, 3).save();
      workouts.of(me).performedOn(day("2026-11-16")).weightReps("ベンチプレス", 60, 10, 3).save();
      workouts.of(sato).performedOn(day("2026-11-10")).weightReps("スクワット", 60, 10, 3).save();
      workouts.of(me).performedOn(day("2026-11-15")).weightReps("スクワット", 60, 10, 1).save();

      assertThat(repository.bodyParts(me, WEEK)).containsExactly(new BodyPartCount("legs", 1));
    }
  }

  @Nested
  class 記録した日 {

    @Test
    void 記録がある日を_重ねずに日付の順で_両端の日を含めて返す() {
      long sato = user("stats_sato3");
      workouts.of(me).performedOn(day("2026-11-15")).save();
      workouts.of(me).performedOn(day("2026-11-09")).visibility("private").save();
      workouts.of(me).performedOn(day("2026-11-12")).save();
      workouts.of(me).performedOn(day("2026-11-12")).save();
      // 期間の外・他人の記録は入らない
      workouts.of(me).performedOn(day("2026-11-08")).save();
      workouts.of(me).performedOn(day("2026-11-16")).save();
      workouts.of(sato).performedOn(day("2026-11-10")).save();

      assertThat(repository.recordedDates(me, WEEK))
          .containsExactly(day("2026-11-09"), day("2026-11-12"), day("2026-11-15"));
    }

    @Test
    void 記録がなければ空() {
      assertThat(repository.recordedDates(me, WEEK)).isEmpty();
    }
  }

  private static LocalDate day(String value) {
    return LocalDate.parse(value);
  }

  private static BigDecimal kg(String value) {
    return new BigDecimal(value);
  }

  private static StoredSet wr(String kg, int reps) {
    return new StoredSet(new BigDecimal(kg).setScale(3), reps, null, null);
  }

  private long user(String username) {
    return userRepository
        .insert(
            new NewUser(
                username,
                "表示名",
                username + "@example.com",
                "hash",
                OffsetDateTime.parse("2026-01-01T00:00:00+09:00")))
        .getId();
  }
}
