package com.okimoto.fitlog.body.controller;

import com.okimoto.fitlog.auth.config.AuthenticatedUser;
import com.okimoto.fitlog.body.repository.BodyRecord;
import com.okimoto.fitlog.body.repository.BodyRecordRepository;
import com.okimoto.fitlog.body.service.BodyRecordService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 体重の記録（docs/api.md A-100〜A-103）。本人のデータだけを扱う */
@RestController
@RequestMapping("/api/body-records")
public class BodyRecordController {

  private final BodyRecordService service;

  public BodyRecordController(BodyRecordService service) {
    this.service = service;
  }

  /** A-100 のリクエスト */
  public record BodyRecordRequest(BigDecimal weight, String weightUnit, BigDecimal bodyFatPct) {}

  /** 体重の記録（A-100・A-101 の要素） */
  public record BodyRecordResponse(
      LocalDate recordedOn, BigDecimal weightKg, BigDecimal bodyFatPct) {

    static BodyRecordResponse from(BodyRecord record) {
      return new BodyRecordResponse(record.recordedOn(), record.weightKg(), record.bodyFatPct());
    }
  }

  /**
   * A-101 の応答。
   *
   * @param nextCursor 続きのカーソル。続きがなければ null
   * @param hasNext 続きがあるか
   */
  public record PageResponse(List<BodyRecordResponse> items, String nextCursor, boolean hasNext) {}

  /** A-103 の点 */
  public record TrendPoint(LocalDate date, BigDecimal weightKg, BigDecimal bodyFatPct) {}

  /** A-103 の応答 */
  public record TrendResponse(List<TrendPoint> points) {}

  /** A-100 その日の体重を記録・上書きする。作ったら 201、上書きしたら 200 */
  @PutMapping("/{date}")
  public ResponseEntity<BodyRecordResponse> record(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestBody BodyRecordRequest request) {
    BodyRecordRepository.Upserted upserted =
        service.record(
            user.id(),
            date,
            new BodyRecordService.Input(
                request.weight(), request.weightUnit(), request.bodyFatPct()));
    return ResponseEntity.status(upserted.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(BodyRecordResponse.from(upserted.record()));
  }

  /** A-101 体重の一覧（日付の新しい順に30件ずつ） */
  @GetMapping
  public PageResponse list(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(required = false) String cursor) {
    BodyRecordService.Page page = service.list(user.id(), cursor);
    return new PageResponse(
        page.items().stream().map(BodyRecordResponse::from).toList(),
        page.nextCursor(),
        page.nextCursor() != null);
  }

  /** A-102 その日の記録を消す */
  @DeleteMapping("/{date}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @AuthenticationPrincipal AuthenticatedUser user,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    service.delete(user.id(), date);
  }

  /** A-103 体重・体脂肪率の推移。range は 3m・1y（なければ 3m） */
  @GetMapping("/trend")
  public TrendResponse trend(
      @AuthenticationPrincipal AuthenticatedUser user,
      @RequestParam(defaultValue = "3m") String range) {
    return new TrendResponse(
        service.trend(user.id(), range).stream()
            .map(r -> new TrendPoint(r.recordedOn(), r.weightKg(), r.bodyFatPct()))
            .toList());
  }
}
