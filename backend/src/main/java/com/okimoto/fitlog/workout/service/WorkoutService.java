package com.okimoto.fitlog.workout.service;

import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.image.calc.ImageType;
import com.okimoto.fitlog.image.service.ImageStorage;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.calc.SetRules;
import com.okimoto.fitlog.workout.calc.WorkoutRules;
import com.okimoto.fitlog.workout.repository.NewWorkout;
import com.okimoto.fitlog.workout.repository.WorkoutDetail;
import com.okimoto.fitlog.workout.repository.WorkoutEdit;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 記録の作成・詳細・編集・削除（F-10〜F-13、docs/api.md A-20〜A-23） */
@Service
public class WorkoutService {

  private final WorkoutRepository workoutRepository;
  private final WorkoutInputValidator validator;
  private final UserRepository userRepository;
  private final ImageStorage imageStorage;
  private final Clock clock;

  public WorkoutService(
      WorkoutRepository workoutRepository,
      WorkoutInputValidator validator,
      UserRepository userRepository,
      ImageStorage imageStorage,
      Clock clock) {
    this.workoutRepository = workoutRepository;
    this.validator = validator;
    this.userRepository = userRepository;
    this.imageStorage = imageStorage;
    this.clock = clock;
  }

  /**
   * A-20 記録の作成。入力のエラーは、画像のエラーも含めてまとめて 400 で返す。
   *
   * <p>画像は S3 に保存してから DB に保存する。DB の保存が取り消されたら、保存した画像は確定後に消える（{@code ImageCleanupListener}）。S3
   * に保存できなければ 500（IMAGE_UPLOAD_FAILED）で、記録も作らない
   *
   * <p>ハッシュタグの保存（段階 4）・確定後の目標の判定（段階 3）は、それぞれを作るときに足す
   *
   * @param images 画像の中身（選んだ順）
   * @return 作った記録の詳細
   */
  @Transactional
  public WorkoutDetail create(long me, WorkoutInput input, List<byte[]> images) {
    lockUser(me);
    List<FieldError> errors = new ArrayList<>(validator.validate(me, input));
    errors.addAll(validator.validateImages(images, 0));
    throwIfAny(errors);
    List<String> imageKeys = store(images);
    OffsetDateTime now = OffsetDateTime.now(clock);
    long id =
        workoutRepository.insert(
            new NewWorkout(
                me,
                input.performedOn(),
                WorkoutRules.normalizeBody(input.body()),
                durationMin(input),
                input.visibility(),
                now,
                exercises(input)));
    workoutRepository.insertImages(id, imageKeys, now);
    return find(me, id);
  }

  /** A-21 記録の詳細。見てはいけない記録は、ない記録と同じ 404 */
  @Transactional(readOnly = true)
  public WorkoutDetail find(long me, long id) {
    return workoutRepository
        .findVisible(id, me)
        .orElseThrow(() -> new ApiException(ErrorCode.WORKOUT_NOT_FOUND));
  }

  /**
   * A-22 記録の編集。種目とセットは送られた内容で置き換え、編集日時を入れる。
   *
   * <p>確かめる順番：記録がない・見てはいけない（404）→ 他人の記録（403）→ 入力のチェック（400）。他人の記録は、入力を直しても編集できないので先に返す
   *
   * <p>画像は、残す画像（{@code keepImageIds} の順）のあとに新しい画像を並べる。外した画像は、保存の確定後に S3 から消す（取り消されたら消さない）
   *
   * @param images 新しい画像の中身（選んだ順）
   * @return 編集したあとの記録の詳細
   */
  @Transactional
  public WorkoutDetail update(long me, long id, WorkoutInput input, List<byte[]> images) {
    lockUser(me);
    requireOwn(me, id);
    List<WorkoutDetail.Image> current = workoutRepository.findImages(id);
    List<Long> keepIds = input.keepImageIds() == null ? List.of() : input.keepImageIds();
    List<FieldError> errors = new ArrayList<>(validator.validate(me, input));
    errors.addAll(validator.validateKeepImageIds(input.keepImageIds(), current));
    errors.addAll(validator.validateImages(images, keepIds.size()));
    throwIfAny(errors);

    Map<Long, String> keyById = new HashMap<>();
    current.forEach(image -> keyById.put(image.id(), image.imageKey()));
    List<String> imageKeys = new ArrayList<>(keepIds.stream().map(keyById::get).toList());
    imageKeys.addAll(store(images));
    OffsetDateTime now = OffsetDateTime.now(clock);
    workoutRepository.update(
        id,
        new WorkoutEdit(
            input.performedOn(),
            WorkoutRules.normalizeBody(input.body()),
            durationMin(input),
            input.visibility(),
            now,
            exercises(input)));
    workoutRepository.replaceImages(id, imageKeys, now);
    current.stream()
        .map(WorkoutDetail.Image::imageKey)
        .filter(key -> !imageKeys.contains(key))
        .forEach(imageStorage::discardAfterCommit);
    return find(me, id);
  }

  /** A-23 記録の削除。記録がない・見てはいけないなら 404、他人の記録なら 403。画像は確定後に S3 から消す */
  @Transactional
  public void delete(long me, long id) {
    requireOwn(me, id);
    List<WorkoutDetail.Image> images = workoutRepository.findImages(id);
    workoutRepository.delete(id);
    images.forEach(image -> imageStorage.discardAfterCommit(image.imageKey()));
  }

  /**
   * 種目を使う書き込みの前に、users の行をロックする。自分専用の種目の削除（A-34）も同じ行をロックするので、「使える種目か」を確かめてから保存するまでの間に、その種目が消されない
   */
  private void lockUser(long me) {
    if (!userRepository.lockById(me)) {
      // トークンは有効なまま、退会した直後など
      throw new ApiException(ErrorCode.UNAUTHENTICATED);
    }
  }

  /** 自分の記録か。記録の行をロックするので、確かめてから書き込むまでの間に、同時の編集・削除で変わらない */
  private void requireOwn(long me, long id) {
    long author =
        workoutRepository
            .lockVisible(id, me)
            .orElseThrow(() -> new ApiException(ErrorCode.WORKOUT_NOT_FOUND));
    if (author != me) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
  }

  private static void throwIfAny(List<FieldError> errors) {
    if (!errors.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, errors);
    }
  }

  /** 新しい画像を S3 に保存し、キーを選んだ順に返す（形式は入力のチェックで確かめてある） */
  private List<String> store(List<byte[]> images) {
    return images.stream()
        .map(bytes -> imageStorage.storeWorkoutImage(bytes, ImageType.detect(bytes).orElseThrow()))
        .toList();
  }

  private static Integer durationMin(WorkoutInput input) {
    return input.durationMin() == null ? null : input.durationMin().intValueExact();
  }

  /** 送られた種目とセットを、保存する形（kg・km・秒）にする */
  private static List<NewWorkout.Exercise> exercises(WorkoutInput input) {
    return input.exercises().stream()
        .map(exercise ->
            new NewWorkout.Exercise(
                exercise.exerciseId(),
                exercise.sets().stream()
                    .map(set -> SetRules.toStored(set, input.weightUnit(), input.distanceUnit()))
                    .toList()))
        .toList();
  }
}
