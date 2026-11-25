package com.okimoto.fitlog.comment.service;

import com.okimoto.fitlog.comment.calc.CommentRules;
import com.okimoto.fitlog.comment.repository.CommentRepository;
import com.okimoto.fitlog.notification.service.NotificationEvent;
import com.okimoto.fitlog.notification.service.NotificationGuard;
import org.springframework.stereotype.Component;

/**
 * コメント・返信（comment・reply）の通知が、まだ正しいか。書かれたコメントが残っていれば正しい。
 *
 * <p>書いてすぐ消したコメントの通知を作ろうとすると、外部キー（コメントがない）で保存に失敗するので、先に確かめて作らない
 */
@Component
public class CommentNotificationGuard implements NotificationGuard {

  private final CommentRepository commentRepository;

  public CommentNotificationGuard(CommentRepository commentRepository) {
    this.commentRepository = commentRepository;
  }

  @Override
  public boolean supports(String type) {
    return CommentRules.COMMENT.equals(type) || CommentRules.REPLY.equals(type);
  }

  @Override
  public boolean isStillValid(NotificationEvent event) {
    return commentRepository.lockExists(event.commentId());
  }
}
