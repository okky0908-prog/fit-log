package com.okimoto.fitlog.follow.calc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** フォローの状態の名前と、記録を見られるか（docs/api.md A-72・A-80、docs/test-plan.md 4.1） */
class FollowRulesTest {

  @ParameterizedTest(name = "DB の {0} は、画面では {1}")
  @CsvSource(
      nullValues = "null",
      value = {"accepted, following", "pending, requested", "null, none"})
  void DBの状態を画面に返す名前にする(String stored, String expected) {
    assertThat(FollowRules.followStatus(stored)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "本人={0}・鍵={1}・自分からの状態={2} → {3}")
  @CsvSource(
      nullValues = "null",
      value = {
        // 本人は、鍵アカウントでもフォローがなくても見られる
        "true,  true,  null,     true",
        // 鍵アカウントでなければ、誰でも見られる
        "false, false, null,     true",
        // 鍵アカウントは、承認済みのフォロワーだけ
        "false, true,  accepted, true",
        "false, true,  pending,  false",
        "false, true,  null,     false",
      })
  void 記録を見られるのは_本人か_鍵アカウントでないか_承認済みのフォロワー(
      boolean me, boolean isPrivate, String myStatus, boolean expected) {
    assertThat(FollowRules.canViewRecords(me, isPrivate, myStatus)).isEqualTo(expected);
  }
}
