package com.okimoto.fitlog.notification.service;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.notification.repository.NotificationRepository;
import com.okimoto.fitlog.notification.repository.NotificationRow;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知の一覧・未読の数・既読（F-111〜F-113、docs/api.md A-120〜A-122） */
@Service
public class NotificationService {

  /** 1ページの件数 */
  static final int PAGE_SIZE = 20;

  /** 未読の数を数えるのをやめる数（画面は「99+」） */
  static final int UNREAD_CAP = 100;

  private final NotificationRepository repository;
  private final Clock clock;

  public NotificationService(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * 1ページ分の通知と、続きのカーソル。
   *
   * @param items 通知（新しい順）
   * @param nextCursor 続きのカーソル。続きがなければ null
   */
  public record Page(List<NotificationRow> items, String nextCursor) {}

  /**
   * A-120 通知一覧。新しい順に20件。一覧を取っただけでは既読にしない。
   *
   * @param cursor 前のページの nextCursor。最初のページは null。壊れていれば 400
   */
  @Transactional(readOnly = true)
  public Page list(long me, String cursor) {
    Cursor.Position after = null;
    if (cursor != null) {
      after =
          Cursor.decode(cursor)
              .orElseThrow(() ->
                  new ApiException(
                      ErrorCode.VALIDATION_FAILED,
                      List.of(new FieldError("cursor", "一覧の続きを読み込めません。最初から読み込み直してください"))));
    }
    // 1件多く取り、続きがあるかを知る
    List<NotificationRow> rows = repository.findPage(me, after, PAGE_SIZE + 1);
    if (rows.size() <= PAGE_SIZE) {
      return new Page(rows, null);
    }
    List<NotificationRow> items = rows.subList(0, PAGE_SIZE);
    NotificationRow last = items.getLast();
    return new Page(items, Cursor.encode(last.createdAt(), last.id()));
  }

  /** A-121 未読の数（100 で数えるのをやめる） */
  @Transactional(readOnly = true)
  public int unreadCount(long me) {
    return repository.countUnread(me, UNREAD_CAP);
  }

  /** A-122 既読にする。自分の未読の通知だけ。何回呼んでも同じ結果になる。空の配列なら何もしない（jOOQ が当てはまらない条件にする） */
  @Transactional
  public void markRead(long me, List<Long> ids) {
    repository.markRead(me, ids, OffsetDateTime.now(clock));
  }
}
