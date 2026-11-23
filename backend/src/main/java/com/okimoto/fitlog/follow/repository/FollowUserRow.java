package com.okimoto.fitlog.follow.repository;

import java.time.OffsetDateTime;

/**
 * フォロー中・フォロワー一覧の1人（A-72・A-73 の元のデータ）。
 *
 * @param id フォローの行（カーソルに使う）
 * @param acceptedAt 承認された日時（並び順とカーソルに使う）
 * @param username ユーザー名
 * @param displayName 表示名
 * @param isPrivate 鍵アカウントか
 * @param iconKey アイコンの S3 のキー。なければ null
 * @param bio 自己紹介
 * @param myStatus 見ている人からこの人へのフォローの状態（pending・accepted）。なければ null
 * @param me 見ている人自身か
 */
public record FollowUserRow(
    long id,
    OffsetDateTime acceptedAt,
    String username,
    String displayName,
    boolean isPrivate,
    String iconKey,
    String bio,
    String myStatus,
    boolean me) {}
