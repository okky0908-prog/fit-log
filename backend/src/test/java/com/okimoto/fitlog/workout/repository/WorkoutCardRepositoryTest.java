package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.FOLLOWS;
import static com.okimoto.fitlog.jooq.Tables.USERS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.calc.ExerciseAggregate;
import com.okimoto.fitlog.workout.calc.StoredSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 記録の一覧の SQL：種目の要約の集計（docs/test-plan.md 5.4）・並び順・カーソル・閲覧の制御（4.1）・新しい投稿の件数。
 *
 * <p>DB はほかのテストと共有するので（結合テストが確定させた記録が残る）、このテストの記録は投稿日時を遠い未来（2999年）にして、全体の一覧の先頭に来るようにする。
 * テストごとにロールバックする
 */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  WorkoutCardRepository.class,
  WorkoutRepository.class,
  UserRepository.class
})
@ActiveProfiles("test")
class WorkoutCardRepositoryTest {

  private static final OffsetDateTime BASE = OffsetDateTime.parse("2999-01-01T10:00:00+09:00");

  @Autowired private WorkoutCardRepository repository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private WorkoutFixture workouts;
  private long me;
  private long sato;

  @BeforeEach
  void setUp() {
    workouts = new WorkoutFixture(dsl, workoutRepository);
    me = user("card_me");
    sato = user("card_sato");
  }

  /** 種目の要約（自分の記録を、自分の記録一覧で取って確かめる） */
  @Nested
  class 種目の要約 {

    @Test
    void 重量と回数_全セットが同じなら_その値() {
      assertThat(single(sets(wr("60", 10), wr("60", 10), wr("60", 10)), "ベンチプレス"))
          .isEqualTo(
              new ExerciseAggregate(
                  1L, "ベンチプレス", "weight_reps", 3, true, kg("60.000"), 10, 30, null, null));
    }

    @Test
    void 重量と回数_値が違えば_一番重いセット() {
      ExerciseAggregate summary = single(sets(wr("60", 10), wr("80", 8), wr("100", 5)), "ベンチプレス");

      assertThat(summary.uniform()).isFalse();
      assertThat(best(summary)).containsExactly(kg("100.000"), 5);
    }

    @Test
    void 重量と回数_同じ重量なら回数が多いセット() {
      assertThat(best(single(sets(wr("100", 3), wr("100", 5)), "ベンチプレス")))
          .containsExactly(kg("100.000"), 5);
    }

    @Test
    void 重量と回数_同じ重量で回数だけ違っても_同じ値ではない() {
      ExerciseAggregate summary = single(sets(wr("60", 10), wr("60", 8)), "ベンチプレス");

      assertThat(summary.uniform()).isFalse();
      assertThat(best(summary)).containsExactly(kg("60.000"), 10);
    }

    @Test
    void 重量と回数_回数が同じで重量だけ違っても_同じ値ではない() {
      ExerciseAggregate summary = single(sets(wr("60", 10), wr("80", 10)), "ベンチプレス");

      assertThat(summary.uniform()).isFalse();
      assertThat(best(summary)).containsExactly(kg("80.000"), 10);
    }

    @Test
    void 重量と回数_0回のセットは除いて選ぶ() {
      assertThat(best(single(sets(wr("120", 0), wr("100", 5)), "ベンチプレス")))
          .containsExactly(kg("100.000"), 5);
    }

    @Test
    void 重量と回数_全部0回なら一番重いセット() {
      assertThat(best(single(sets(wr("110", 0), wr("120", 0)), "ベンチプレス")))
          .containsExactly(kg("120.000"), 0);
    }

    @Test
    void 重量と回数_1セットだけでも全セットが同じ() {
      ExerciseAggregate summary = single(sets(wr("60", 10)), "ベンチプレス");

      assertThat(summary.uniform()).isTrue();
      assertThat(summary.setCount()).isEqualTo(1);
    }

    @Test
    void 回数のみ_全セットが同じなら_その回数() {
      ExerciseAggregate summary = single(sets(reps(20), reps(20), reps(20)), "腕立て伏せ");

      assertThat(summary.uniform()).isTrue();
      assertThat(summary.bestReps()).isEqualTo(20);
      assertThat(summary.bestWeightKg()).isNull();
    }

    @Test
    void 回数のみ_値が違えば_合計() {
      ExerciseAggregate summary = single(sets(reps(20), reps(20), reps(15)), "腕立て伏せ");

      assertThat(summary.uniform()).isFalse();
      assertThat(summary.totalReps()).isEqualTo(55);
    }

    @Test
    void 時間と距離_1回() {
      assertThat(single(sets(run(1800, "5")), "ランニング"))
          .isEqualTo(
              new ExerciseAggregate(
                  48L, "ランニング", "time_distance", 1, false, null, null, null, 1800, km("5.000")));
    }

    @Test
    void 時間と距離_距離なし() {
      ExerciseAggregate summary = single(sets(run(1800, null)), "ランニング");

      assertThat(summary.totalDurationSec()).isEqualTo(1800);
      assertThat(summary.totalDistanceKm()).isNull();
    }

    @Test
    void 時間と距離_2回なら合計() {
      ExerciseAggregate summary = single(sets(run(1800, "5"), run(1200, "3")), "ランニング");

      assertThat(summary.setCount()).isEqualTo(2);
      assertThat(summary.totalDurationSec()).isEqualTo(3000);
      assertThat(summary.totalDistanceKm()).isEqualTo(km("8.000"));
    }

    @Test
    void 時間と距離_距離のないセットは距離の合計に入らない() {
      ExerciseAggregate summary = single(sets(run(1800, "5"), run(1200, null)), "ランニング");

      assertThat(summary.totalDurationSec()).isEqualTo(3000);
      assertThat(summary.totalDistanceKm()).isEqualTo(km("5.000"));
    }

    @Test
    void 先頭5種目を並び順のとおりに返し_同じ種目が2回なら2行() {
      workouts
          .of(me)
          .createdAt(BASE)
          .weightReps("ベンチプレス", 60, 10, 1)
          .reps("腕立て伏せ", 20, 1)
          .weightReps("ベンチプレス", 40, 15, 1)
          .weightReps("スクワット", 100, 5, 1)
          .timeDistance("ランニング", 1800, 5.0)
          .save();

      WorkoutCardRow card = mine().getFirst();

      assertThat(card.exercises())
          .extracting(ExerciseAggregate::name)
          .containsExactly("ベンチプレス", "腕立て伏せ", "ベンチプレス", "スクワット", "ランニング");
      assertThat(card.otherExerciseCount()).isZero();
    }

    @Test
    void 種目が6つなら_先頭5種目と_ほか1種目() {
      exercises(6);

      WorkoutCardRow card = mine().getFirst();

      assertThat(card.exercises()).hasSize(5);
      assertThat(card.otherExerciseCount()).isEqualTo(1);
    }

    @Test
    void 種目が20なら_ほか15種目() {
      exercises(20);

      assertThat(mine().getFirst().otherExerciseCount()).isEqualTo(15);
    }

    @Test
    void 投稿者と画像も返す() {
      long id = workouts.of(me).createdAt(BASE).body("脚の日").save();
      workoutRepository.insertImages(id, List.of("workouts/b.jpg", "workouts/a.jpg"), BASE);

      WorkoutCardRow card = mine().getFirst();

      assertThat(card.username()).isEqualTo("card_me");
      assertThat(card.body()).isEqualTo("脚の日");
      assertThat(card.images())
          .extracting(WorkoutDetail.Image::imageKey, WorkoutDetail.Image::sortOrder)
          .containsExactly(tuple("workouts/b.jpg", (short) 1), tuple("workouts/a.jpg", (short) 2));
    }

    private ExerciseAggregate single(List<StoredSet> sets, String exercise) {
      workouts.of(me).createdAt(BASE).exercise(exercise, sets).save();
      return mine().getFirst().exercises().getFirst();
    }

    private void exercises(int count) {
      WorkoutFixture.Builder builder = workouts.of(me).createdAt(BASE);
      IntStream.range(0, count).forEach(i -> builder.reps("腕立て伏せ", i + 1, 1));
      builder.save();
    }

    private List<WorkoutCardRow> mine() {
      return repository.findByUser(me, me, null, 21);
    }
  }

  @Nested
  class 全体タイムライン {

    @Test
    void 投稿日時の新しい順_同じ日時ならIDの大きい順_実施日には関係しない() {
      long old = workouts.of(me).createdAt(BASE).performedOn(LocalDate.of(2026, 11, 20)).save();
      long sameTime1 = workouts.of(sato).createdAt(BASE.plusMinutes(1)).save();
      long sameTime2 = workouts.of(me).createdAt(BASE.plusMinutes(1)).save();
      // 過去の日付の記録をあとから登録しても、タイムラインでは新しい投稿
      long latePast =
          workouts
              .of(sato)
              .createdAt(BASE.plusMinutes(2))
              .performedOn(LocalDate.of(2026, 1, 1))
              .save();

      assertThat(ids(repository.findTimeline(me, false, null, 4)))
          .containsExactly(latePast, sameTime2, sameTime1, old);
    }

    @Test
    void カーソルより後から続きを返す() {
      long first = workouts.of(me).createdAt(BASE).save();
      long second = workouts.of(me).createdAt(BASE.plusMinutes(1)).save();
      long third = workouts.of(me).createdAt(BASE.plusMinutes(1)).save();

      List<WorkoutCardRow> rest =
          repository.findTimeline(me, false, new Cursor.Position(BASE.plusMinutes(1), third), 2);

      assertThat(ids(rest)).containsExactly(second, first);
    }

    @Test
    void 見てはいけない記録は出ない() {
      long locked = user("card_locked");
      dsl.update(USERS).set(USERS.IS_PRIVATE, true).where(USERS.ID.eq(locked)).execute();
      follow(me, locked, "pending");
      long publicOne = workouts.of(sato).createdAt(BASE).save();
      workouts.of(sato).createdAt(BASE).visibility("followers").save();
      workouts.of(sato).createdAt(BASE).visibility("private").save();
      workouts.of(locked).createdAt(BASE).save();
      long myPrivate = workouts.of(me).createdAt(BASE).visibility("private").save();

      // このテストの記録は投稿日時が同じなので、先頭の5件に入る（ほかのテストの記録は、それより後に並ぶ）
      assertThat(
              repository.findTimeline(me, false, null, 5).stream()
                  .filter(row -> row.createdAt().isEqual(BASE))
                  .map(WorkoutCardRow::id))
          .containsExactly(myPrivate, publicOne);
    }

    @Test
    void 承認済みのフォロワーには_フォロワー限定も出る() {
      follow(me, sato, "accepted");
      long followers = workouts.of(sato).createdAt(BASE).visibility("followers").save();

      assertThat(ids(repository.findTimeline(me, false, null, 1))).containsExactly(followers);
    }
  }

  @Nested
  class フォロー中タイムライン {

    @Test
    void 自分と承認済みのフォロー相手の記録だけ() {
      long tanaka = user("card_tanaka");
      long locked = user("card_locked");
      long reverse = user("card_reverse");
      dsl.update(USERS).set(USERS.IS_PRIVATE, true).where(USERS.ID.eq(locked)).execute();
      follow(me, sato, "accepted");
      follow(me, locked, "pending");
      // 向きを取り違えない：相手からフォローされているだけ
      follow(reverse, me, "accepted");
      long mine = workouts.of(me).createdAt(BASE).save();
      long satos = workouts.of(sato).createdAt(BASE).visibility("followers").save();
      workouts.of(tanaka).createdAt(BASE).save();
      workouts.of(locked).createdAt(BASE).save();
      workouts.of(reverse).createdAt(BASE).save();
      workouts.of(sato).createdAt(BASE).visibility("private").save();

      assertThat(ids(repository.findTimeline(me, true, null, 21)))
          .containsExactlyInAnyOrder(mine, satos);
    }
  }

  @Nested
  class ユーザーの記録一覧 {

    @Test
    void 実施日の新しい順_同じ日は投稿日時の新しい順_それも同じならIDの大きい順() {
      long older =
          workouts
              .of(sato)
              .performedOn(LocalDate.of(2026, 11, 19))
              .createdAt(BASE.plusDays(1))
              .save();
      long sameDay1 =
          workouts.of(sato).performedOn(LocalDate.of(2026, 11, 20)).createdAt(BASE).save();
      long sameDay2 =
          workouts
              .of(sato)
              .performedOn(LocalDate.of(2026, 11, 20))
              .createdAt(BASE.plusMinutes(1))
              .save();
      long sameDay3 =
          workouts
              .of(sato)
              .performedOn(LocalDate.of(2026, 11, 20))
              .createdAt(BASE.plusMinutes(1))
              .save();
      workouts.of(me).performedOn(LocalDate.of(2026, 11, 21)).createdAt(BASE).save();

      assertThat(ids(repository.findByUser(sato, me, null, 21)))
          .containsExactly(sameDay3, sameDay2, sameDay1, older);
    }

    @Test
    void カーソルより後から続きを返す() {
      LocalDate day = LocalDate.of(2026, 11, 20);
      long older =
          workouts.of(sato).performedOn(day.minusDays(1)).createdAt(BASE.plusDays(1)).save();
      long first = workouts.of(sato).performedOn(day).createdAt(BASE).save();
      long second = workouts.of(sato).performedOn(day).createdAt(BASE).save();

      List<WorkoutCardRow> rest =
          repository.findByUser(sato, me, new Cursor.DatedPosition(day, BASE, second), 21);

      assertThat(ids(rest)).containsExactly(first, older);
    }

    @Test
    void 他人の見てはいけない記録は出ず_自分の記録はすべて出る() {
      long publicOne = workouts.of(sato).createdAt(BASE).save();
      workouts.of(sato).createdAt(BASE).visibility("followers").save();
      workouts.of(sato).createdAt(BASE).visibility("private").save();
      long myPrivate = workouts.of(me).createdAt(BASE).visibility("private").save();

      assertThat(ids(repository.findByUser(sato, me, null, 21))).containsExactly(publicOne);
      assertThat(ids(repository.findByUser(me, me, null, 21))).containsExactly(myPrivate);
    }
  }

  @Nested
  class 新しい投稿の件数 {

    @Test
    void sinceより後の_見てよい他人の記録を数え_自分の記録は数えない() {
      long since = workouts.of(sato).createdAt(BASE).save();
      workouts.of(sato).createdAt(BASE).save();
      workouts.of(sato).createdAt(BASE).visibility("private").save();
      workouts.of(me).createdAt(BASE).save();

      assertThat(repository.countNew(me, false, since, 100)).isEqualTo(1);
    }

    @Test
    void フォロー中は_承認済みのフォロー相手の記録だけを数える() {
      long tanaka = user("card_tanaka");
      follow(me, sato, "accepted");
      long since = maxWorkoutId();
      workouts.of(sato).createdAt(BASE).save();
      workouts.of(tanaka).createdAt(BASE).save();

      assertThat(repository.countNew(me, true, since, 100)).isEqualTo(1);
      assertThat(repository.countNew(me, false, since, 100)).isEqualTo(2);
    }

    @Test
    void 上限で数えるのをやめる_99件_100件_101件() {
      long since = maxWorkoutId();
      saveMany(99);
      assertThat(repository.countNew(me, false, since, 100)).isEqualTo(99);
      saveMany(1);
      assertThat(repository.countNew(me, false, since, 100)).isEqualTo(100);
      saveMany(1);
      assertThat(repository.countNew(me, false, since, 100)).isEqualTo(100);
    }

    private void saveMany(int count) {
      IntStream.range(0, count).forEach(i -> workouts.of(sato).createdAt(BASE).save());
    }
  }

  private long maxWorkoutId() {
    Long max = dsl.select(org.jooq.impl.DSL.max(WORKOUTS.ID)).from(WORKOUTS).fetchSingle().value1();
    return max == null ? 0 : max;
  }

  private static List<Long> ids(List<WorkoutCardRow> rows) {
    return rows.stream().map(WorkoutCardRow::id).toList();
  }

  private static List<Object> best(ExerciseAggregate summary) {
    return java.util.Arrays.asList(summary.bestWeightKg(), summary.bestReps());
  }

  private static List<StoredSet> sets(StoredSet... sets) {
    return List.of(sets);
  }

  private static StoredSet wr(String kg, int reps) {
    return new StoredSet(new BigDecimal(kg).setScale(3), reps, null, null);
  }

  private static StoredSet reps(int reps) {
    return new StoredSet(null, reps, null, null);
  }

  private static StoredSet run(int durationSec, String km) {
    return new StoredSet(
        null, null, durationSec, km == null ? null : new BigDecimal(km).setScale(3));
  }

  private static BigDecimal kg(String value) {
    return new BigDecimal(value);
  }

  private static BigDecimal km(String value) {
    return new BigDecimal(value);
  }

  private void follow(long follower, long followee, String status) {
    dsl.insertInto(FOLLOWS)
        .set(FOLLOWS.FOLLOWER_ID, follower)
        .set(FOLLOWS.FOLLOWEE_ID, followee)
        .set(FOLLOWS.STATUS, status)
        .set(FOLLOWS.CREATED_AT, BASE)
        .set(FOLLOWS.ACCEPTED_AT, "accepted".equals(status) ? BASE : null)
        .execute();
  }

  private long user(String username) {
    return userRepository
        .insert(new NewUser(username, "表示名", username + "@example.com", "hash", BASE))
        .getId();
  }
}
