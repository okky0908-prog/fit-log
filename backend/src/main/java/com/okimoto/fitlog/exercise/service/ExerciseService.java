package com.okimoto.fitlog.exercise.service;

import com.okimoto.fitlog.common.controller.ApiError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.exercise.calc.ExerciseRules;
import com.okimoto.fitlog.exercise.repository.ExerciseRepository;
import com.okimoto.fitlog.exercise.repository.NewExercise;
import com.okimoto.fitlog.jooq.tables.records.ExercisesRecord;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 種目の検索と、自分専用の種目の追加・変更・削除（F-14〜F-16、docs/api.md A-30・A-32〜A-34） */
@Service
public class ExerciseService {

  /** 自分専用の種目の上限 */
  static final int MAX_CUSTOM_EXERCISES = 100;

  /** 最近使った種目の件数 */
  private static final int RECENT_LIMIT = 10;

  /** name_key の列の長さ。NFKC で文字が増える名前（「㌔」→「キロ」）は、入力が30文字以内でも入らない */
  private static final int MAX_NAME_KEY_LENGTH = 30;

  private static final String NAME_LENGTH_MESSAGE = "種目の名前は1〜30文字で入力してください";

  private final ExerciseRepository exerciseRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public ExerciseService(
      ExerciseRepository exerciseRepository, UserRepository userRepository, Clock clock) {
    this.exerciseRepository = exerciseRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /**
   * A-30 種目の検索。
   *
   * @param query 名前の部分一致。null・空白だけなら絞らない
   * @param bodyPart 部位。null なら絞らない
   */
  @Transactional(readOnly = true)
  public List<ExercisesRecord> search(long me, String query, String bodyPart) {
    String pattern = query == null || query.isBlank() ? null : ExerciseRules.containsPattern(query);
    return exerciseRepository.search(me, pattern, bodyPart);
  }

  /** A-32 自分専用の種目の追加。上限（409）→ 名前の重複（409）の順に確かめる */
  @Transactional
  public ExercisesRecord create(long me, String name, String bodyPart, String recordType) {
    String trimmed = name.strip();
    String key = nameKey(trimmed);
    lockUser(me);
    if (exerciseRepository.countCustom(me) >= MAX_CUSTOM_EXERCISES) {
      throw new ApiException(ErrorCode.EXERCISE_LIMIT_EXCEEDED);
    }
    requireNameAvailable(me, key, null);
    return exerciseRepository.insertCustom(
        new NewExercise(
            me,
            trimmed,
            key,
            bodyPart,
            recordType,
            ExerciseRules.defaultMets(bodyPart, recordType),
            OffsetDateTime.now(clock)));
  }

  /** A-33 自分専用の種目の名前・部位の変更。部位が変わると標準の METs も変わる */
  @Transactional
  public ExercisesRecord update(long me, long id, String name, String bodyPart) {
    String trimmed = name.strip();
    String key = nameKey(trimmed);
    // 同じ人の追加・変更が同時に来て、同じ名前が2つできるのを防ぐ（DB の一意制約でも止まるが、409 で返すため）
    lockUser(me);
    ExercisesRecord exercise = findOwn(me, id);
    requireNameAvailable(me, key, id);
    return exerciseRepository.updateCustom(
        id,
        trimmed,
        key,
        bodyPart,
        ExerciseRules.defaultMets(bodyPart, exercise.getRecordType()),
        OffsetDateTime.now(clock));
  }

  /** A-31 最近使った種目（10件まで） */
  @Transactional(readOnly = true)
  public List<ExercisesRecord> recent(long me) {
    return exerciseRepository.recent(me, RECENT_LIMIT);
  }

  /**
   * A-34 自分専用の種目の削除。記録で使っている種目は 409。テンプレートの中のその種目は一緒に消える。
   *
   * <p>目標で使っている種目を消せない決まりは、目標の表を作るときに足す
   */
  @Transactional
  public void delete(long me, long id) {
    // 記録の保存（作成・編集）も同じ行をロックするので、使っていないと確かめたあとに、その種目を使う記録は保存されない
    lockUser(me);
    findOwn(me, id);
    if (exerciseRepository.usedInWorkouts(id)) {
      throw new ApiException(ErrorCode.EXERCISE_IN_USE);
    }
    exerciseRepository.deleteCustom(id);
  }

  private static String nameKey(String name) {
    String key = ExerciseRules.nameKey(name);
    if (key.codePointCount(0, key.length()) > MAX_NAME_KEY_LENGTH) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          List.of(new ApiError.FieldError("name", NAME_LENGTH_MESSAGE)));
    }
    return key;
  }

  private void lockUser(long me) {
    if (!userRepository.lockById(me)) {
      // トークンは有効なまま、退会した直後など
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
  }

  private ExercisesRecord findOwn(long me, long id) {
    return exerciseRepository
        .findCustom(id, me)
        .orElseThrow(() -> new ApiException(ErrorCode.EXERCISE_NOT_FOUND));
  }

  private void requireNameAvailable(long me, String key, Long excludeId) {
    if (exerciseRepository.nameTaken(me, key, excludeId)) {
      throw new ApiException(
          ErrorCode.EXERCISE_NAME_TAKEN,
          List.of(new ApiError.FieldError("name", ErrorCode.EXERCISE_NAME_TAKEN.message())));
    }
  }
}
