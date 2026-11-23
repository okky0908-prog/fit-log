package com.okimoto.fitlog.user.service;

/**
 * プロフィールの編集の入力（A-82 の {@code data} のパート）。チェックは {@link com.okimoto.fitlog.user.calc.ProfileRules}
 * で、アイコンの誤りと一緒に返す。
 *
 * @param displayName 表示名（1〜50文字）
 * @param bio 自己紹介（160文字まで。空なら null にする）
 */
public record ProfileInput(String displayName, String bio) {}
