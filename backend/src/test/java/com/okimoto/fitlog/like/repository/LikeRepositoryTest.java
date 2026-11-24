package com.okimoto.fitlog.like.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.support.WorkoutFixture;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** ナイストレーニングの読み書き。本物の DB で確かめる（テストごとにロールバックする） */
@JooqTest
@Import({
  TestcontainersConfiguration.class,
  LikeRepository.class,
  WorkoutRepository.class,
  UserRepository.class
})
@ActiveProfiles("test")
class LikeRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-25T10:00:00+09:00");

  @Autowired private LikeRepository likeRepository;
  @Autowired private WorkoutRepository workoutRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private long me;
  private long sato;
  private long workout;

  @BeforeEach
  void setUp() {
    me = user("like_me");
    sato = user("like_sato");
    workout = new WorkoutFixture(dsl, workoutRepository).of(sato).save();
  }

  @Test
  void 付けると1件になり_もう一度付けても増えない() {
    assertThat(likeRepository.insertIfAbsent(workout, me, NOW)).isTrue();
    assertThat(likeRepository.insertIfAbsent(workout, me, NOW)).isFalse();

    assertThat(likeRepository.count(workout)).isEqualTo(1);
    assertThat(likeRepository.lockExists(workout, me)).isTrue();
  }

  @Test
  void 人ごとに数え_ほかの記録の分は数えない() {
    long other = new WorkoutFixture(dsl, workoutRepository).of(sato).save();
    likeRepository.insertIfAbsent(workout, me, NOW);
    likeRepository.insertIfAbsent(workout, sato, NOW);
    likeRepository.insertIfAbsent(other, me, NOW);

    assertThat(likeRepository.count(workout)).isEqualTo(2);
    assertThat(likeRepository.count(other)).isEqualTo(1);
  }

  @Test
  void 取り消すと消え_付いていなければ何もしない() {
    likeRepository.insertIfAbsent(workout, me, NOW);

    assertThat(likeRepository.delete(workout, me)).isTrue();
    assertThat(likeRepository.delete(workout, me)).isFalse();

    assertThat(likeRepository.count(workout)).isZero();
    assertThat(likeRepository.lockExists(workout, me)).isFalse();
  }

  private long user(String username) {
    return userRepository
        .insert(new NewUser(username, "表示名", username + "@example.com", "hash", NOW))
        .getId();
  }
}
