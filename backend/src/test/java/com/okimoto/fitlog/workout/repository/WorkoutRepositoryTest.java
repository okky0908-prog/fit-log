package com.okimoto.fitlog.workout.repository;

import static com.okimoto.fitlog.jooq.Tables.USERS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUTS;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_EXERCISES;
import static com.okimoto.fitlog.jooq.Tables.WORKOUT_SETS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.okimoto.fitlog.exercise.calc.ExerciseRules;
import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.exercise.repository.NewExercise;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.calc.StoredSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** 記録の読み書きと閲覧の制御。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  WorkoutRepository.class,
  UserRepository.class,
  ExerciseRepository.class
})
@ActiveProfiles("test")
class WorkoutRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-21T10:00:00+09:00");
  private static final long BENCH_PRESS = 1L;
  private static final long PUSH_UP = 8L;
  private static final long RUNNING = 48L;

  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private DSLContext dsl;

  private long author;
  private long other;

  @BeforeEach
  void setUp() {
    author = userRepository.insert(user("author")).getId();
    other = userRepository.insert(user("other")).getId();
  }

  @Test
  void 保存した記録を_種目とセットの並び順のとおりに取り出せる() {
    long custom = customExercise(author, "自分の種目");
    long id =
        workoutRepository.insert(
            new NewWorkout(
                author,
                LocalDate.of(2026, 11, 20),
                "脚の日\n#脚トレ",
                75,
                "public",
                NOW,
                List.of(
                    exercise(BENCH_PRESS, weightReps("60.000", 10), weightReps("100.000", 5)),
                    exercise(RUNNING, new StoredSet(null, null, 1800, new BigDecimal("5.000"))),
                    // 同じ種目を2回入れてもよい
                    exercise(BENCH_PRESS, weightReps("40.000", 15)),
                    exercise(custom, new StoredSet(null, 20, null, null)))));

    WorkoutDetail detail = workoutRepository.findVisible(id, author).orElseThrow();

    assertThat(detail.id()).isEqualTo(id);
    assertThat(detail.userId()).isEqualTo(author);
    assertThat(detail.username()).isEqualTo("author");
    assertThat(detail.displayName()).isEqualTo("表示名");
    assertThat(detail.authorPrivate()).isFalse();
    assertThat(detail.performedOn()).isEqualTo(LocalDate.of(2026, 11, 20));
    assertThat(detail.createdAt()).isEqualTo(NOW);
    assertThat(detail.editedAt()).isNull();
    assertThat(detail.body()).isEqualTo("脚の日\n#脚トレ");
    assertThat(detail.durationMin()).isEqualTo((short) 75);
    assertThat(detail.visibility()).isEqualTo("public");
    assertThat(detail.exercises())
        .extracting(WorkoutDetail.Exercise::name)
        .containsExactly("ベンチプレス", "ランニング", "ベンチプレス", "自分の種目");
    assertThat(detail.exercises().getFirst().sets())
        .containsExactly(weightReps("60.000", 10), weightReps("100.000", 5));
    WorkoutDetail.Exercise running = detail.exercises().get(1);
    assertThat(running.exerciseId()).isEqualTo(RUNNING);
    assertThat(running.bodyPart()).isEqualTo("cardio");
    assertThat(running.recordType()).isEqualTo("time_distance");
    assertThat(running.isRunning()).isTrue();
    assertThat(running.sets())
        .containsExactly(new StoredSet(null, null, 1800, new BigDecimal("5.000")));
    assertThat(detail.exercises().get(3).sets())
        .containsExactly(new StoredSet(null, 20, null, null));
  }

  @Test
  void ひとことと時間がなくても保存できる() {
    long id = save(author, "private");

    WorkoutDetail detail = workoutRepository.findVisible(id, author).orElseThrow();

    assertThat(detail.body()).isEmpty();
    assertThat(detail.durationMin()).isNull();
  }

  /** 閲覧の制御（docs/test-plan.md 4.1 ①）のうち、フォローを使わない列。フォロワー（承認済み・リクエスト中・逆向き）の列は、フォローの表を作るときに足す */
  @ParameterizedTest(name = "[{index}] 公開範囲 {0}・鍵アカウント {1} → 関係ない人が見られる {2}")
  @CsvSource({
    "public, false, true",
    "public, true, false",
    "followers, false, false",
    "followers, true, false",
    "private, false, false",
    "private, true, false",
  })
  void 本人はすべて見られ_関係ない人は鍵アカウントでない人の公開の記録だけ見られる(
      String visibility, boolean authorPrivate, boolean byStranger) {
    dsl.update(USERS).set(USERS.IS_PRIVATE, authorPrivate).where(USERS.ID.eq(author)).execute();
    long id = save(author, visibility);

    assertThat(workoutRepository.findVisible(id, author)).as("本人").isPresent();
    assertThat(workoutRepository.findVisible(id, other).isPresent())
        .as("関係なし")
        .isEqualTo(byStranger);
  }

  @Test
  void ない記録は空() {
    assertThat(workoutRepository.findVisible(Long.MAX_VALUE, author)).isEmpty();
  }

  @Test
  void どの記録方式の形にも当てはまらないセットは_DBでも保存できない() {
    // 重量と時間が両方ある
    StoredSet broken = new StoredSet(new BigDecimal("60.000"), 10, 60, null);

    assertThatThrownBy(() ->
            workoutRepository.insert(
                new NewWorkout(
                    author,
                    LocalDate.of(2026, 11, 20),
                    "",
                    null,
                    "public",
                    NOW,
                    List.of(exercise(BENCH_PRESS, broken)))))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 記録で使える種目は_用意された種目と自分の種目だけ() {
    long mine = customExercise(author, "自分の種目");
    long others = customExercise(other, "他人の種目");

    assertThat(
            exerciseRepository.findUsable(
                author, List.of(BENCH_PRESS, PUSH_UP, mine, others, 99_999L)))
        .containsOnlyKeys(BENCH_PRESS, PUSH_UP, mine)
        .containsEntry(PUSH_UP, "reps")
        .containsEntry(mine, "weight_reps");
  }

  @Test
  void 編集の前に_見てよい記録の投稿者を取る_見てはいけない記録とない記録は空() {
    long mine = save(author, "private");
    long othersPublic = save(other, "public");
    long othersPrivate = save(other, "private");

    assertThat(workoutRepository.lockVisible(mine, author)).contains(author);
    assertThat(workoutRepository.lockVisible(othersPublic, author)).contains(other);
    assertThat(workoutRepository.lockVisible(othersPrivate, author)).isEmpty();
    assertThat(workoutRepository.lockVisible(Long.MAX_VALUE, author)).isEmpty();
  }

  @Test
  void 編集すると_項目と種目とセットが置き換わり_編集日時と更新日時が入る() {
    long id =
        workoutRepository.insert(
            new NewWorkout(
                author,
                LocalDate.of(2026, 11, 20),
                "前のひとこと",
                30,
                "public",
                NOW,
                List.of(
                    exercise(BENCH_PRESS, weightReps("60.000", 10), weightReps("70.000", 8)),
                    exercise(PUSH_UP, new StoredSet(null, 20, null, null)))));
    long untouched = save(author, "public");
    OffsetDateTime editedAt = NOW.plusHours(1);

    workoutRepository.update(
        id,
        new WorkoutEdit(
            LocalDate.of(2026, 11, 19),
            "後のひとこと",
            null,
            "followers",
            editedAt,
            List.of(exercise(RUNNING, new StoredSet(null, null, 600, null)))));

    WorkoutDetail detail = workoutRepository.findVisible(id, author).orElseThrow();
    assertThat(detail.performedOn()).isEqualTo(LocalDate.of(2026, 11, 19));
    assertThat(detail.body()).isEqualTo("後のひとこと");
    assertThat(detail.durationMin()).isNull();
    assertThat(detail.visibility()).isEqualTo("followers");
    assertThat(detail.createdAt()).as("投稿日時は変わらない").isEqualTo(NOW);
    assertThat(detail.editedAt()).isEqualTo(editedAt);
    assertThat(dsl.fetchValue(WORKOUTS.UPDATED_AT, WORKOUTS.ID.eq(id))).isAtSameInstantAs(editedAt);
    assertThat(detail.exercises())
        .extracting(WorkoutDetail.Exercise::name)
        .containsExactly("ランニング");
    assertThat(detail.exercises().getFirst().sets())
        .containsExactly(new StoredSet(null, null, 600, null));
    // 前の種目とセットの行は残らない
    assertThat(dsl.fetchCount(WORKOUT_EXERCISES, WORKOUT_EXERCISES.WORKOUT_ID.eq(id))).isEqualTo(1);
    assertThat(setCount(id)).isEqualTo(1);
    // ほかの記録は変わらない
    WorkoutDetail other = workoutRepository.findVisible(untouched, author).orElseThrow();
    assertThat(other.editedAt()).isNull();
    assertThat(other.exercises()).extracting(WorkoutDetail.Exercise::name).containsExactly("腕立て伏せ");
  }

  @Test
  void 削除すると_種目とセットも一緒に消え_ほかの記録は残る() {
    long id = save(author, "public");
    long kept = save(author, "public");

    workoutRepository.delete(id);

    assertThat(workoutRepository.findVisible(id, author)).isEmpty();
    assertThat(dsl.fetchCount(WORKOUT_EXERCISES, WORKOUT_EXERCISES.WORKOUT_ID.eq(id))).isZero();
    assertThat(setCount(id)).isZero();
    assertThat(workoutRepository.findVisible(kept, author)).isPresent();
    assertThat(setCount(kept)).isEqualTo(1);
  }

  private int setCount(long workoutId) {
    return dsl.fetchCount(
        WORKOUT_SETS,
        WORKOUT_SETS.WORKOUT_EXERCISE_ID.in(
            dsl.select(WORKOUT_EXERCISES.ID)
                .from(WORKOUT_EXERCISES)
                .where(WORKOUT_EXERCISES.WORKOUT_ID.eq(workoutId))));
  }

  private long save(long userId, String visibility) {
    return workoutRepository.insert(
        new NewWorkout(
            userId,
            LocalDate.of(2026, 11, 20),
            "",
            null,
            visibility,
            NOW,
            List.of(exercise(PUSH_UP, new StoredSet(null, 20, null, null)))));
  }

  private long customExercise(long ownerId, String name) {
    return exerciseRepository
        .insertCustom(
            new NewExercise(
                ownerId,
                name,
                ExerciseRules.nameKey(name),
                "chest",
                "weight_reps",
                new BigDecimal("3.5"),
                NOW))
        .getId();
  }

  private static NewWorkout.Exercise exercise(long exerciseId, StoredSet... sets) {
    return new NewWorkout.Exercise(exerciseId, List.of(sets));
  }

  private static StoredSet weightReps(String kg, int reps) {
    return new StoredSet(new BigDecimal(kg), reps, null, null);
  }

  private static NewUser user(String username) {
    return new NewUser(username, "表示名", username + "@example.com", "hash", NOW);
  }
}
