package com.okimoto.fitlog.exercise.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.exercise.calc.ExerciseRules;
import com.okimoto.fitlog.jooq.tables.records.ExercisesRecord;
import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 種目の読み書き。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({TestcontainersConfiguration.class, ExerciseRepository.class, UserRepository.class})
@ActiveProfiles("test")
class ExerciseRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-21T10:00:00+09:00");

  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private UserRepository userRepository;

  private long me;
  private long other;

  @BeforeEach
  void setUp() {
    me = userRepository.insert(user("me_user")).getId();
    other = userRepository.insert(user("other_user")).getId();
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
