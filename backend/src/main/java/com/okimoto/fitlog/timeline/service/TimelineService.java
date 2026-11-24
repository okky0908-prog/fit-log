package com.okimoto.fitlog.timeline.service;

import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.common.service.CursorParams;
import com.okimoto.fitlog.user.repository.UserRepository;
import com.okimoto.fitlog.workout.repository.WorkoutCardRepository;
import com.okimoto.fitlog.workout.repository.WorkoutCardRow;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** タイムライン（F-20〜F-24、A-50〜A-53）と、そのユーザーの記録一覧（F-62、A-81） */
@Service
public class TimelineService {

  /** 一覧の1ページの件数 */
  static final int PAGE_SIZE = 20;

  /** 新しい投稿は、この件数で数えるのをやめる（画面は「99+」のように出さず、件数のまま出す） */
  static final int NEW_COUNT_LIMIT = 100;

  private final WorkoutCardRepository repository;
  private final UserRepository userRepository;

  public TimelineService(WorkoutCardRepository repository, UserRepository userRepository) {
    this.repository = repository;
    this.userRepository = userRepository;
  }

  /**
   * 一覧の1ページと、続きのカーソル。
   *
   * @param items 一覧の記録
   * @param nextCursor 続きのカーソル。続きがなければ null
   */
  public record Page(List<WorkoutCardRow> items, String nextCursor) {}

  /**
   * A-50・A-51 タイムライン。
   *
   * @param followingOnly true ならフォロー中タイムライン、false なら全体タイムライン
   */
  @Transactional(readOnly = true)
  public Page timeline(long me, boolean followingOnly, String cursor) {
    Cursor.Position after = CursorParams.position(cursor);
    return page(
        repository.findTimeline(me, followingOnly, after, PAGE_SIZE + 1),
        last -> Cursor.encode(last.createdAt(), last.id()));
  }

  /** A-52・A-53 新しい投稿の件数（100件まで） */
  @Transactional(readOnly = true)
  public int newCount(long me, boolean followingOnly, long since) {
    return repository.countNew(me, followingOnly, since, NEW_COUNT_LIMIT);
  }

  /** A-81 そのユーザーの記録一覧。いなければ 404。鍵アカウントで見られなければ、閲覧の条件で0件になる */
  @Transactional(readOnly = true)
  public Page userWorkouts(long me, String username, String cursor) {
    long owner =
        userRepository
            .findByUsernameIgnoreCase(username)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND))
            .getId();
    Cursor.DatedPosition after = CursorParams.datedPosition(cursor);
    return page(
        repository.findByUser(owner, me, after, PAGE_SIZE + 1),
        last -> Cursor.encodeDated(last.performedOn(), last.createdAt(), last.id()));
  }

  /** 1件多く取った結果から、1ページと続きのカーソルを作る */
  private static Page page(List<WorkoutCardRow> rows, Function<WorkoutCardRow, String> cursorOf) {
    if (rows.size() <= PAGE_SIZE) {
      return new Page(rows, null);
    }
    List<WorkoutCardRow> items = rows.subList(0, PAGE_SIZE);
    return new Page(items, cursorOf.apply(items.getLast()));
  }
}
