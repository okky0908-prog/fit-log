package com.okimoto.fitlog.comment.repository;

/**
 * 返信・削除・返信の続きの対象のコメント（見てよい投稿のコメントだけ）。
 *
 * @param id コメント
 * @param workoutId 投稿
 * @param workoutAuthorId 投稿者
 * @param parentId 返信元。返信でなければ null
 * @param userId 書いた人
 */
public record CommentTarget(
    long id, long workoutId, long workoutAuthorId, Long parentId, long userId) {}
