package com.okimoto.fitlog.follow.calc;

/** フォローの状態の名前と、記録を見られるかの決まり（docs/api.md A-72・A-80） */
public final class FollowRules {

  /** follows.status の承認済み */
  public static final String ACCEPTED = "accepted";

  /** follows.status のリクエスト中（承認待ち） */
  public static final String PENDING = "pending";

  /** フォローしていない */
  public static final String NONE = "none";

  private FollowRules() {}

  /**
   * DB の状態（accepted・pending・なし）を、API で返す名前（following・requested・none）にする。
   *
   * @param stored follows.status。行がなければ null
   */
  public static String followStatus(String stored) {
    if (stored == null) {
      return NONE;
    }
    return ACCEPTED.equals(stored) ? "following" : "requested";
  }

  /**
   * 記録・フォロー一覧を見られるか。
   *
   * @param me 見ている人自身か
   * @param isPrivate 相手が鍵アカウントか
   * @param myStatus 見ている人から相手へのフォローの状態（follows.status）。なければ null
   */
  public static boolean canViewRecords(boolean me, boolean isPrivate, String myStatus) {
    return me || !isPrivate || ACCEPTED.equals(myStatus);
  }
}
