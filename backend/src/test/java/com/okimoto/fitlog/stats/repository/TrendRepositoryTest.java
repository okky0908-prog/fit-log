package com.okimoto.fitlog.stats.repository;

import static com.okimoto.fitlog.jooq.Tables.EXERCISES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.exercise.repository.NewExercise;
import com.okimoto.fitlog.stats.calc.DayAggregate;
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
 * 種目ごとの推移の SQL（docs/test-plan.md 5.11 の推定 1RM、docs/database.md「種目ごとの推移」）。本人の記録だけを、公開範囲に関係なく集計する。
 *
 * <p>テストごとに新しいユーザーを作り、ロールバックする
 */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  TrendRepository.class,
  ExerciseRepository.class,
  WorkoutRepository.class,
  UserRepository.class
})
@ActiveProfiles("test")
class TrendRepositoryTest {

  @Autowired private TrendRepository repository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private WorkoutFixture workouts;
  private long me;
  private long benchPress;
  private long pushUp;
  private long running;

  @BeforeEach
  void setUp() {
    workouts = new WorkoutFixture(dsl, workoutRepository);
    me = user("trend_me");
    benchPress = exerciseId("ベンチプレス");
    pushUp = exerciseId("腕立て伏せ");
    running = exerciseId("ランニング");
  }

  @Nested
  class 記録した種目 {

    @Test
    void 自分が記録した種目を_部位の順_用意された種目の順_自分の種目の名前の順に_最後に記録した日つきで返す() {
      long mine = custom("マイプレス", "chest");
      workouts
          .of(me)
          .performedOn(day("2026-11-01"))
          .weightReps("スクワット", 100, 5, 1)
          .weightReps("ベンチプレス", 60, 10, 1)
          .save();
      workouts.of(me).performedOn(day("2026-11-10")).weightReps("ベンチプレス", 60, 10, 1).save();
      // 自分のみの記録も入る
      workouts
          .of(me)
          .performedOn(day("2026-10-01"))
          .visibility("private")
          .exercise(mine, List.of(weight("50", 10)))
          .timeDistance("ランニング", 1800, 5.0)
          .save();
      // 他人だけが記録した種目は入らない
      workouts.of(user("trend_sato")).performedOn(day("2026-11-11")).reps("懸垂", 10, 1).save();

      assertThat(repository.usedExercises(me))
          .extracting(used -> used.exercise().getName(), UsedExercise::lastPerformedOn)
          .containsExactly(
              tuple("ベンチプレス", day("2026-11-10")),
              tuple("マイプレス", day("2026-10-01")),
              tuple("スクワット", day("2026-11-01")),
              tuple("ランニング", day("2026-10-01")));
    }

    @Test
    void 記録がなければ空() {
      assertThat(repository.usedExercises(me)).isEmpty();
    }
  }

  @Nested
  class 重量と回数の推移 {

    /** docs/test-plan.md 5.11 の推定 1RM の表。1日に1つずつ記録する */
    @Test
    void 推定1RMは1回から10回のセットだけ_1回は重量そのもの_小数第3位に四捨五入() {
      long zero = weights("2026-11-01", weight("100", 0));
      long one = weights("2026-11-02", weight("100", 1));
      long ten = weights("2026-11-03", weight("100", 10));
      long five = weights("2026-11-04", weight("100", 5));
      long eleven = weights("2026-11-05", weight("100", 11));
      long mixed = weights("2026-11-06", weight("80", 10), weight("100", 1));

      assertThat(repository.days(me, benchPress, "weight_reps", null))
          .containsExactly(
              // 0回のセットは、最大重量にも数えない
              new DayAggregate(day("2026-11-01"), zero, null, null, null, 0, 0, null, null, 0),
              new DayAggregate(
                  day("2026-11-02"), one, kg("100.000"), kg("100.000"), 1, 1, 1, null, null, 0),
              // 100 ×（1 + 10 ÷ 30）＝ 133.333…
              new DayAggregate(
                  day("2026-11-03"), ten, kg("100.000"), kg("133.333"), 10, 10, 10, null, null, 0),
              // 116.666… は切り上げる側
              new DayAggregate(
                  day("2026-11-04"), five, kg("100.000"), kg("116.667"), 5, 5, 5, null, null, 0),
              // 11回以上は推定 1RM を計算しない。最大重量は出る
              new DayAggregate(
                  day("2026-11-05"), eleven, kg("100.000"), null, 11, 11, 11, null, null, 0),
              // 80 ×（1 + 10 ÷ 30）＝ 106.666… と 100 の大きい方
              new DayAggregate(
                  day("2026-11-06"),
                  mixed,
                  kg("100.000"),
                  kg("106.667"),
                  1,
                  11,
                  10,
                  null,
                  null,
                  0));
    }

    @Test
    void 最大重量の回数は_同じ重量のセットのうち多い方() {
      weights("2026-11-01", weight("100", 3), weight("100", 5), weight("90", 8));

      DayAggregate point = repository.days(me, benchPress, "weight_reps", null).getFirst();
      assertThat(point.maxWeightKg()).isEqualTo(kg("100.000"));
      assertThat(point.maxWeightReps()).isEqualTo(5);
    }

    @Test
    void 同じ日の記録は1つの点にまとめ_記録のIDは最大重量を出した記録() {
      long heavy =
          workouts
              .of(me)
              .performedOn(day("2026-11-01"))
              .createdAt(at("2026-11-01T08:00:00+09:00"))
              .exercise(benchPress, List.of(weight("100", 1)))
              .save();
      workouts
          .of(me)
          .performedOn(day("2026-11-01"))
          .createdAt(at("2026-11-01T20:00:00+09:00"))
          .exercise(benchPress, List.of(weight("80", 10)))
          .save();

      assertThat(repository.days(me, benchPress, "weight_reps", null))
          .singleElement()
          .satisfies(point -> {
            assertThat(point.workoutId()).isEqualTo(heavy);
            assertThat(point.maxE1rmKg()).isEqualTo(kg("106.667"));
            assertThat(point.totalReps()).isEqualTo(11);
          });
    }

    @Test
    void 挙がらなかった0回のセットは_重くても一番よいセットにしない() {
      long lifted =
          workouts
              .of(me)
              .performedOn(day("2026-11-01"))
              .createdAt(at("2026-11-01T08:00:00+09:00"))
              .exercise(benchPress, List.of(weight("100", 5)))
              .save();
      workouts
          .of(me)
          .performedOn(day("2026-11-01"))
          .createdAt(at("2026-11-01T20:00:00+09:00"))
          .exercise(benchPress, List.of(weight("120", 0)))
          .save();

      DayAggregate point = repository.days(me, benchPress, "weight_reps", null).getFirst();
      assertThat(point.workoutId()).isEqualTo(lifted);
      assertThat(point.maxWeightKg()).isEqualTo(kg("100.000"));
      assertThat(point.maxWeightReps()).isEqualTo(5);
    }

    @Test
    void 最大重量が同じ記録が2つあれば_あとに投稿した記録() {
      workouts
          .of(me)
          .performedOn(day("2026-11-01"))
          .createdAt(at("2026-11-01T08:00:00+09:00"))
          .exercise(benchPress, List.of(weight("100", 1)))
          .save();
      long later =
          workouts
              .of(me)
              .performedOn(day("2026-11-01"))
              .createdAt(at("2026-11-01T20:00:00+09:00"))
              .exercise(benchPress, List.of(weight("100", 1)))
              .save();

      assertThat(repository.days(me, benchPress, "weight_reps", null).getFirst().workoutId())
          .isEqualTo(later);
    }
  }

  @Nested
  class 回数のみと時間と距離の推移 {

    @Test
    void 回数のみは_合計回数と最大回数_記録のIDは最大回数を出した記録() {
      workouts.of(me).performedOn(day("2026-11-01")).reps("腕立て伏せ", 20, 2).save();
      long most = workouts.of(me).performedOn(day("2026-11-01")).reps("腕立て伏せ", 30, 1).save();

      assertThat(repository.days(me, pushUp, "reps", null))
          .containsExactly(
              new DayAggregate(day("2026-11-01"), most, null, null, null, 70, 30, null, null, 0));
    }

    @Test
    void 時間と距離は_合計時間と合計距離と距離のあるセットの時間_記録のIDは最長距離を出した記録() {
      workouts
          .of(me)
          .performedOn(day("2026-11-01"))
          .exercise(running, List.of(run(1800, "5.000"), run(600, null)))
          .save();
      long longest =
          workouts
              .of(me)
              .performedOn(day("2026-11-01"))
              .exercise(running, List.of(run(3000, "10.000")))
              .save();

      assertThat(repository.days(me, running, "time_distance", null))
          .containsExactly(
              new DayAggregate(
                  day("2026-11-01"),
                  longest,
                  null,
                  null,
                  null,
                  null,
                  null,
                  5400,
                  km("15.000"),
                  4800));
    }

    @Test
    void 距離のあるセットがなければ_時間の長い記録() {
      workouts
          .of(me)
          .performedOn(day("2026-11-01"))
          .exercise(running, List.of(run(600, null)))
          .save();
      long longer =
          workouts
              .of(me)
              .performedOn(day("2026-11-01"))
              .exercise(running, List.of(run(1200, null)))
              .save();

      assertThat(repository.days(me, running, "time_distance", null))
          .containsExactly(
              new DayAggregate(
                  day("2026-11-01"), longer, null, null, null, null, null, 1800, null, 0));
    }
  }

  @Nested
  class 期間と対象 {

    @Test
    void 始まりの日を含み_前の日は入らない_実施日の古い順() {
      weights("2026-11-10", weight("60", 10));
      weights("2026-08-12", weight("50", 10));
      weights("2026-08-11", weight("40", 10));

      assertThat(repository.days(me, benchPress, "weight_reps", day("2026-08-12")))
          .extracting(DayAggregate::date)
          .containsExactly(day("2026-08-12"), day("2026-11-10"));
      assertThat(repository.days(me, benchPress, "weight_reps", null)).hasSize(3);
    }

    @Test
    void 他人の記録とほかの種目は入らない() {
      workouts
          .of(user("trend_sato"))
          .performedOn(day("2026-11-01"))
          .exercise(benchPress, List.of(weight("100", 1)))
          .save();
      workouts.of(me).performedOn(day("2026-11-01")).weightReps("スクワット", 100, 1, 1).save();

      assertThat(repository.days(me, benchPress, "weight_reps", null)).isEmpty();
    }
  }

  @Nested
  class 自己ベストと推定1RMの最高 {

    @Test
    void 自己ベストは1回以上のセットの最大重量_同じなら最初に挙げた記録() {
      weights("2026-09-01", weight("90", 5));
      long first = weights("2026-10-01", weight("100", 1));
      weights("2026-11-01", weight("100", 2), weight("120", 0));
      workouts
          .of(user("trend_sato"))
          .performedOn(day("2026-11-02"))
          .exercise(benchPress, List.of(weight("150", 1)))
          .save();

      assertThat(repository.personalBest(me, benchPress))
          .contains(new BestWeight(kg("100.000"), day("2026-10-01"), first));
    }

    @Test
    void 推定1RMの最高は1回から10回のセットから_同じなら最初の記録() {
      // 100 × 10 ＝ 133.333、120 × 11 は対象外、100 × 10 をもう一度
      long first = weights("2026-10-01", weight("100", 10));
      weights("2026-11-01", weight("120", 11), weight("100", 10), weight("110", 1));

      assertThat(repository.bestE1rm(me, benchPress))
          .contains(new BestWeight(kg("133.333"), day("2026-10-01"), first));
    }

    @Test
    void 対象のセットがなければ空() {
      long id = weights("2026-11-01", weight("120", 0), weight("100", 12));

      assertThat(repository.personalBest(me, pushUp)).isEmpty();
      assertThat(repository.bestE1rm(me, benchPress)).isEmpty();
      // 0回のセットは自己ベストに数えない
      assertThat(repository.personalBest(me, benchPress))
          .contains(new BestWeight(kg("100.000"), day("2026-11-01"), id));
    }
  }

  private long weights(String performedOn, StoredSet... sets) {
    return workouts.of(me).performedOn(day(performedOn)).exercise(benchPress, List.of(sets)).save();
  }

  private long custom(String name, String bodyPart) {
    return exerciseRepository
        .insertCustom(
            new NewExercise(
                me,
                name,
                name,
                bodyPart,
                "weight_reps",
                new BigDecimal("6.0"),
                at("2026-01-01T00:00:00+09:00")))
        .getId();
  }

  private long exerciseId(String name) {
    return dsl.select(EXERCISES.ID)
        .from(EXERCISES)
        .where(EXERCISES.NAME.eq(name))
        .and(EXERCISES.OWNER_ID.isNull())
        .fetchSingle()
        .value1();
  }

  private static StoredSet weight(String kg, int reps) {
    return new StoredSet(kg(kg), reps, null, null);
  }

  private static StoredSet run(int durationSec, String distanceKm) {
    return new StoredSet(null, null, durationSec, distanceKm == null ? null : km(distanceKm));
  }

  private static BigDecimal kg(String value) {
    return new BigDecimal(value);
  }

  private static BigDecimal km(String value) {
    return new BigDecimal(value);
  }

  private static LocalDate day(String value) {
    return LocalDate.parse(value);
  }

  private static OffsetDateTime at(String value) {
    return OffsetDateTime.parse(value);
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
