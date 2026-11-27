package com.okimoto.fitlog.exercise.repository;

import static com.okimoto.fitlog.jooq.Tables.WORKOUT_EXERCISES;
import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.exercise.calc.ExerciseRules;
import com.okimoto.fitlog.jooq.tables.records.ExercisesRecord;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 種目の読み書き。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  ExerciseRepository.class,
  UserRepository.class,
  WorkoutRepository.class
})
@ActiveProfiles("test")
class ExerciseRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-21T10:00:00+09:00");

  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private DSLContext dsl;

  private long me;
  private long other;
  private WorkoutFixture workouts;

  @BeforeEach
  void setUp() {
    me = userRepository.insert(user("me_user")).getId();
    other = userRepository.insert(user("other_user")).getId();
    workouts = new WorkoutFixture(dsl, workoutRepository);
  }

  @Test
  void 最初から用意された種目のキーは_アプリの正規化と同じ() {
    assertThat(exerciseRepository.search(me, null, null))
        .hasSize(58)
        .allSatisfy(e -> assertThat(e.getNameKey()).isEqualTo(ExerciseRules.nameKey(e.getName())));
  }

  @Test
  void 条件がなければ_用意された種目と自分の種目を_部位の順_用意された種目の順_自分の種目の名前の順に返す() {
    add(me, "Zプレス", "chest");
    add(me, "Aプレス", "chest");
    add(me, "有酸素の自作", "cardio");
    add(other, "他人の種目", "chest");

    List<ExercisesRecord> found = exerciseRepository.search(me, null, null);

    assertThat(found).hasSize(61);
    // 胸：用意された8種目のあとに、自分の種目が名前の順
    assertThat(names(found.subList(6, 10))).containsExactly("ディップス", "腕立て伏せ", "Aプレス", "Zプレス");
    assertThat(names(found.subList(10, 11))).containsExactly("デッドリフト");
    assertThat(names(found.subList(59, 61))).containsExactly("縄跳び", "有酸素の自作");
    assertThat(names(found)).doesNotContain("他人の種目");
  }

  @Test
  void 名前の部分一致と部位で絞り込める() {
    add(me, "ケーブルプレス", "shoulders");

    assertThat(
            names(exerciseRepository.search(me, ExerciseRules.containsPattern("プレス"), "shoulders")))
        .containsExactly("ショルダープレス", "ダンベルショルダープレス", "ケーブルプレス");
    assertThat(names(exerciseRepository.search(me, null, "abs"))).hasSize(5);
    assertThat(exerciseRepository.search(me, ExerciseRules.containsPattern("ﾍﾞﾝﾁ"), null))
        .extracting(ExercisesRecord::getName)
        // 半角カナで探しても当たる。部位の順（胸 → 腕）に並ぶ
        .containsExactly("ベンチプレス", "インクラインベンチプレス", "ナローベンチプレス");
  }

  @Test
  void 検索の文字の百分率記号と下線は_どんな文字にも当たる記号として扱わない() {
    add(me, "100%スクワット", "legs");
    add(me, "A_B", "legs");
    add(me, "AxB", "legs");

    assertThat(names(exerciseRepository.search(me, ExerciseRules.containsPattern("%"), null)))
        .containsExactly("100%スクワット");
    assertThat(names(exerciseRepository.search(me, ExerciseRules.containsPattern("_"), null)))
        .containsExactly("A_B");
  }

  @Test
  void 名前が使われているか_用意された種目と自分の種目で判定し_他人の種目と変更中の種目自身は除く() {
    long mine = add(me, "ブルガリアンスクワット", "legs").getId();
    add(other, "他人の種目", "legs");

    assertThat(exerciseRepository.nameTaken(me, ExerciseRules.nameKey("ベンチプレス"), null)).isTrue();
    assertThat(exerciseRepository.nameTaken(me, ExerciseRules.nameKey("ブルガリアンスクワット"), null))
        .isTrue();
    assertThat(exerciseRepository.nameTaken(me, ExerciseRules.nameKey("他人の種目"), null)).isFalse();
    assertThat(exerciseRepository.nameTaken(me, ExerciseRules.nameKey("ブルガリアンスクワット"), mine))
        .isFalse();
    // 自分の種目を除いても、用意された種目と同じ名前は使えない
    assertThat(exerciseRepository.nameTaken(me, ExerciseRules.nameKey("ベンチプレス"), mine)).isTrue();
  }

  @Test
  void 自分専用の種目を数える_他人の種目は数えない() {
    add(me, "種目1", "legs");
    add(me, "種目2", "legs");
    add(other, "種目3", "legs");

    assertThat(exerciseRepository.countCustom(me)).isEqualTo(2);
  }

  @Test
  void 自分専用の種目を保存すると_ランニングでない種目として保存する() {
    ExercisesRecord saved = add(me, "ブルガリアンスクワット", "legs");

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getOwnerId()).isEqualTo(me);
    assertThat(saved.getNameKey()).isEqualTo("ブルガリアンスクワット");
    assertThat(saved.getRecordType()).isEqualTo("weight_reps");
    assertThat(saved.getMets()).isEqualByComparingTo("3.5");
    assertThat(saved.getIsRunning()).isFalse();
    assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  void 自分専用の種目は_本人のものだけ取れる_用意された種目は取れない() {
    long mine = add(me, "自分の種目", "legs").getId();

    assertThat(exerciseRepository.findCustom(mine, me)).isPresent();
    assertThat(exerciseRepository.findCustom(mine, other)).isEmpty();
    assertThat(exerciseRepository.findCustom(1L, me)).isEmpty();
  }

  @Test
  void 見てよい種目は_用意された種目と本人の種目_他人の種目と存在しない種目は空() {
    long mine = add(me, "自分の種目", "legs").getId();

    assertThat(exerciseRepository.findVisible(1L, me)).isPresent();
    assertThat(exerciseRepository.findVisible(mine, me)).isPresent();
    assertThat(exerciseRepository.findVisible(mine, other)).isEmpty();
    assertThat(exerciseRepository.findVisible(Long.MAX_VALUE, me)).isEmpty();
  }

  @Test
  void 名前_部位_METsを変えると_記録方式は変えずに更新日時を新しくする() {
    long mine = add(me, "自分の種目", "legs").getId();

    ExercisesRecord updated =
        exerciseRepository.updateCustom(
            mine, "Cardio Ｘ", "cardio x", "cardio", new BigDecimal("3.5"), NOW.plusHours(1));

    assertThat(updated.getName()).isEqualTo("Cardio Ｘ");
    assertThat(updated.getNameKey()).isEqualTo("cardio x");
    assertThat(updated.getBodyPart()).isEqualTo("cardio");
    assertThat(updated.getRecordType()).isEqualTo("weight_reps");
    assertThat(updated.getCreatedAt()).isEqualTo(NOW);
    assertThat(updated.getUpdatedAt()).isEqualTo(NOW.plusHours(1));
  }

  @Test
  void 自分専用の種目を消す() {
    long mine = add(me, "自分の種目", "legs").getId();

    exerciseRepository.deleteCustom(mine);

    assertThat(exerciseRepository.findCustom(mine, me)).isEmpty();
  }

  @Test
  void 最近使った種目は_最後に使った記録の実施日_投稿日時の新しい順で_記録の中は種目の順() {
    long custom = add(me, "自分の種目", "legs").getId();
    workouts
        .of(me)
        .performedOn(LocalDate.of(2026, 11, 18))
        .createdAt(at("2026-11-18T19:00"))
        .reps("スクワット", 10, 1)
        .reps("ベンチプレス", 10, 1)
        .save();
    // 同じ実施日で、あとに投稿した記録。ID の順と投稿日時の順が逆になるように、先に保存する
    workouts
        .of(me)
        .performedOn(LocalDate.of(2026, 11, 20))
        .createdAt(at("2026-11-20T20:00"))
        .reps("ベンチプレス", 10, 1)
        .reps("懸垂", 10, 1)
        .save();
    workouts
        .of(me)
        .performedOn(LocalDate.of(2026, 11, 20))
        .createdAt(at("2026-11-20T10:00"))
        .reps("デッドリフト", 5, 1)
        .save();
    // 投稿は一番新しいが、実施日は前
    long withCustom =
        workouts
            .of(me)
            .performedOn(LocalDate.of(2026, 11, 19))
            .createdAt(at("2026-11-21T08:00"))
            .reps("ランジ", 10, 1)
            .save();
    dsl.insertInto(WORKOUT_EXERCISES)
        .set(WORKOUT_EXERCISES.WORKOUT_ID, withCustom)
        .set(WORKOUT_EXERCISES.EXERCISE_ID, custom)
        .set(WORKOUT_EXERCISES.SORT_ORDER, (short) 2)
        .execute();
    // 他人の記録で使った種目は入らない
    workouts
        .of(other)
        .performedOn(LocalDate.of(2026, 11, 21))
        .createdAt(at("2026-11-21T09:00"))
        .reps("ディップス", 10, 1)
        .save();

    assertThat(names(exerciseRepository.recent(me, 10)))
        // ベンチプレスは、最後に使った記録（11/20 20:00）の位置に1回だけ出る
        .containsExactly("ベンチプレス", "懸垂", "デッドリフト", "ランジ", "自分の種目", "スクワット");
  }

  @Test
  void 最近使った種目は_指定の件数まで_記録がなければ空() {
    assertThat(exerciseRepository.recent(me, 10)).isEmpty();

    WorkoutFixture.Builder workout = workouts.of(me);
    List<String> eleven =
        List.of(
            "ベンチプレス",
            "インクラインベンチプレス",
            "ダンベルプレス",
            "ダンベルフライ",
            "チェストプレス",
            "ケーブルクロスオーバー",
            "ディップス",
            "腕立て伏せ",
            "デッドリフト",
            "ベントオーバーロウ",
            "ラットプルダウン");
    eleven.forEach(name -> workout.reps(name, 10, 1));
    workout.save();

    assertThat(names(exerciseRepository.recent(me, 10)))
        .containsExactlyElementsOf(eleven.subList(0, 10));
  }

  @Test
  void 記録で使っている種目か() {
    long used = add(me, "使っている種目", "legs").getId();
    long unused = add(me, "使っていない種目", "legs").getId();
    long workoutId = workouts.of(me).save();
    dsl.insertInto(WORKOUT_EXERCISES)
        .set(WORKOUT_EXERCISES.WORKOUT_ID, workoutId)
        .set(WORKOUT_EXERCISES.EXERCISE_ID, used)
        .set(WORKOUT_EXERCISES.SORT_ORDER, (short) 2)
        .execute();

    assertThat(exerciseRepository.usedInWorkouts(used)).isTrue();
    assertThat(exerciseRepository.usedInWorkouts(unused)).isFalse();
  }

  private static OffsetDateTime at(String tokyoDateTime) {
    return OffsetDateTime.parse(tokyoDateTime + ":00+09:00");
  }

  private ExercisesRecord add(long ownerId, String name, String bodyPart) {
    return exerciseRepository.insertCustom(
        new NewExercise(
            ownerId,
            name,
            ExerciseRules.nameKey(name),
            bodyPart,
            "weight_reps",
            new BigDecimal("3.5"),
            NOW));
  }

  private static List<String> names(List<ExercisesRecord> exercises) {
    return exercises.stream().map(ExercisesRecord::getName).toList();
  }

  private static NewUser user(String username) {
    return new NewUser(username, "表示名", username + "@example.com", "hash", NOW);
  }
}
