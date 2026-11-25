package com.okimoto.fitlog.comment.service;

import com.okimoto.fitlog.comment.calc.CommentRules;
import com.okimoto.fitlog.comment.repository.CommentRepository;
import com.okimoto.fitlog.comment.repository.CommentRow;
import com.okimoto.fitlog.comment.repository.CommentTarget;
import com.okimoto.fitlog.comment.repository.NewComment;
import com.okimoto.fitlog.comment.repository.ReplyRow;
import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.common.service.CursorParams;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import com.okimoto.fitlog.workout.repository.WorkoutRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * コメント・返信（F-30〜F-33、docs/api.md A-60〜A-62・A-65）。
 *
 * <ul>
 *   <li>投稿が見てはいけない・ないなら 404（WORKOUT_NOT_FOUND）。コメントを指す API（削除・返信の続き・返信元）は COMMENT_NOT_FOUND
 *   <li>返信は1段だけ：返信への返信は、同じ返信元への返信にし、返信先はその返信を書いた人にする（F-33）
 *   <li>通知は確定後に作る（コメント → 投稿者、返信 → 返信先の人。同じ人なら1件。{@link CommentRules#notices}）
 * </ul>
 */
@Service
public class CommentService {

  /** 一覧・返信の続きの1ページの件数 */
  static final int PAGE_SIZE = 20;

  private final CommentRepository commentRepository;
  private final WorkoutRepository workoutRepository;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public CommentService(
      CommentRepository commentRepository,
      WorkoutRepository workoutRepository,
      ApplicationEventPublisher events,
      Clock clock) {
    this.commentRepository = commentRepository;
    this.workoutRepository = workoutRepository;
    this.events = events;
    this.clock = clock;
  }

  /**
   * 一覧の1ページと、続きのカーソル。
   *
   * @param items 一覧
   * @param nextCursor 続きのカーソル。続きがなければ null
   */
  public record Page<T>(List<T> items, String nextCursor) {}

  /**
   * 投稿したコメント・返信と、投稿後のコメント数。
   *
   * @param comment 書いたコメント・返信（返信の形）
   * @param reply 返信か
   * @param commentCount 投稿後のコメント数（返信を含む）
   */
  public record Posted(ReplyRow comment, boolean reply, int commentCount) {}

  /** A-60 コメント一覧 */
  @Transactional(readOnly = true)
  public Page<CommentRow> list(long me, long workoutId, String cursor) {
    if (!workoutRepository.existsVisible(workoutId, me)) {
      throw new ApiException(ErrorCode.WORKOUT_NOT_FOUND);
    }
    Cursor.Position after = CursorParams.position(cursor);
    List<CommentRow> rows = commentRepository.findPage(workoutId, me, after, PAGE_SIZE + 1);
    return page(rows, last -> Cursor.encode(last.createdAt(), last.id()));
  }

  /**
   * A-61 コメント・返信の投稿。確かめる順番：本文（400）→ 投稿（404）→ 返信元（404）。
   *
   * @param replyToCommentId 返信するコメント・返信。コメントなら null
   */
  @Transactional
  public Posted post(long me, long workoutId, String body, Long replyToCommentId) {
    CommentRules.problem(body)
        .ifPresent(error -> {
          throw new ApiException(ErrorCode.VALIDATION_FAILED, List.of(error));
        });
    long author =
        workoutRepository
            .lockVisibleForShare(workoutId, me)
            .orElseThrow(() -> new ApiException(ErrorCode.WORKOUT_NOT_FOUND));
    Long parentId = null;
    Long replyToUserId = null;
    if (replyToCommentId != null) {
      CommentTarget target =
          commentRepository
              .findVisibleTarget(replyToCommentId, me)
              .filter(t -> t.workoutId() == workoutId)
              .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
      // 返信への返信は、同じ返信元への返信にする（返信は1段だけ）
      parentId = target.parentId() != null ? target.parentId() : target.id();
      replyToUserId = target.userId();
    }
    long id =
        commentRepository.insert(
            new NewComment(
                workoutId,
                me,
                parentId,
                replyToUserId,
                CommentRules.body(body),
                OffsetDateTime.now(clock)));
    for (CommentRules.Notice notice : CommentRules.notices(me, author, replyToUserId)) {
      events.publishEvent(
          new NotificationEvent(notice.type(), notice.recipientId(), me, workoutId, id));
    }
    return new Posted(
        commentRepository.findOne(id, me).orElseThrow(),
        parentId != null,
        commentRepository.count(workoutId));
  }

  /**
   * A-62 コメントの削除。消せるのは書いた本人と投稿者。返信元なら返信も消える。
   *
   * @return 削除後のコメント数（返信を含む）
   */
  @Transactional
  public int delete(long me, long commentId) {
    CommentTarget target = findTarget(me, commentId);
    if (target.userId() != me && target.workoutAuthorId() != me) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    commentRepository.delete(commentId);
    return commentRepository.count(target.workoutId());
  }

  /** A-65 返信の続き。commentId は返信でないコメント（返信の ID なら 404） */
  @Transactional
  public Page<ReplyRow> replies(long me, long commentId, String cursor) {
    CommentTarget target = findTarget(me, commentId);
    if (target.parentId() != null) {
      throw new ApiException(ErrorCode.COMMENT_NOT_FOUND);
    }
    Cursor.Position after = CursorParams.position(cursor);
    List<ReplyRow> rows = commentRepository.findReplies(commentId, me, after, PAGE_SIZE + 1);
    return page(rows, last -> Cursor.encode(last.createdAt(), last.id()));
  }

  private CommentTarget findTarget(long me, long commentId) {
    return commentRepository
        .findVisibleTarget(commentId, me)
        .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
  }

  /** 1件多く取った結果から、1ページと続きのカーソルを作る */
  private static <T> Page<T> page(List<T> rows, java.util.function.Function<T, String> cursorOf) {
    if (rows.size() <= PAGE_SIZE) {
      return new Page<>(rows, null);
    }
    List<T> items = rows.subList(0, PAGE_SIZE);
    return new Page<>(items, cursorOf.apply(items.getLast()));
  }
}
