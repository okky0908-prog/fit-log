package com.okimoto.fitlog.body.service;

import com.okimoto.fitlog.body.calc.BodyRecordRules;
import com.okimoto.fitlog.body.repository.BodyRecord;
import com.okimoto.fitlog.body.repository.BodyRecordRepository;
import com.okimoto.fitlog.body.repository.NewBodyRecord;
import com.okimoto.fitlog.common.calc.Cursor;
import com.okimoto.fitlog.common.calc.JapanTime;
import com.okimoto.fitlog.common.controller.ApiError.FieldError;
import com.okimoto.fitlog.common.controller.ApiException;
import com.okimoto.fitlog.common.controller.ErrorCode;
import com.okimoto.fitlog.common.service.CursorParams;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 体重の記録（F-90〜F-92、docs/api.md A-100〜A-103）。本人のデータだけを扱う。
 *
 * <p>体重の目標の達成の判定（A-100 の確定後）は、目標を作る段階 3 で足す
 */
@Service
public class BodyRecordService {

  /** 一覧の1ページの件数（S-25） */
  static final int PAGE_SIZE = 30;

  private final BodyRecordRepository repository;
  private final Clock clock;

  public BodyRecordService(BodyRecordRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /**
   * A-100 の入力。
   *
   * @param weight 体重（weightUnit の単位）
   * @param weightUnit kg・lb
   * @param bodyFatPct 体脂肪率（%）。任意
   */
  public record Input(BigDecimal weight, String weightUnit, BigDecimal bodyFatPct) {}

  /**
   * 一覧の1ページ。
   *
   * @param nextCursor 続きのカーソル。続きがなければ null
   */
  public record Page(List<BodyRecord> items, String nextCursor) {}

  /** A-100 その日の体重を記録・上書きする。未来の日付・範囲と刻みに合わない値は 400（すべての項目のエラーを返す） */
  @Transactional
  public BodyRecordRepository.Upserted record(long me, LocalDate date, Input input) {
    List<FieldError> errors = new ArrayList<>();
    if (date.isAfter(JapanTime.today(clock))) {
      errors.add(new FieldError("recordedOn", "未来の日付は選べません"));
    }
    BodyRecordRules.check(input.weight(), input.weightUnit(), input.bodyFatPct())
        .forEach((field, message) -> errors.add(new FieldError(field, message)));
    if (!errors.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, errors);
    }
    return repository.upsert(
        new NewBodyRecord(
            me, date, BodyRecordRules.toKg(input.weight(), input.weightUnit()), input.bodyFatPct()),
        OffsetDateTime.now(clock));
  }

  /** A-101 日付の新しい順に30件ずつ */
  @Transactional(readOnly = true)
  public Page list(long me, String cursor) {
    LocalDate before = CursorParams.date(cursor);
    // 1件多く取り、続きがあるかを知る
    List<BodyRecord> rows = repository.findPage(me, before, PAGE_SIZE + 1);
    if (rows.size() <= PAGE_SIZE) {
      return new Page(rows, null);
    }
    List<BodyRecord> items = rows.subList(0, PAGE_SIZE);
    return new Page(items, Cursor.encodeDate(items.getLast().recordedOn()));
  }

  /** A-102 その日の記録を消す。なければ 404 */
  @Transactional
  public void delete(long me, LocalDate date) {
    if (!repository.delete(me, date)) {
      throw new ApiException(ErrorCode.BODY_RECORD_NOT_FOUND);
    }
  }

  /** A-103 体重・体脂肪率の推移（日付の古い順）。range は 3m・1y（それ以外は 400） */
  @Transactional(readOnly = true)
  public List<BodyRecord> trend(long me, String range) {
    LocalDate from =
        BodyRecordRules.trendFrom(range, JapanTime.today(clock))
            .orElseThrow(() -> new ApiException(ErrorCode.MALFORMED_REQUEST));
    return repository.trend(me, from);
  }
}
