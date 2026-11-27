package com.okimoto.fitlog.body.repository;

import static com.okimoto.fitlog.jooq.Tables.BODY_RECORDS;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.noCondition;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Records;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

/** 体重の記録（docs/database.md「body_records」、F-90〜F-92）。本人の記録だけを扱う */
@Repository
public class BodyRecordRepository {

  /**
   * 記録・上書きの結果。
   *
   * @param record 保存したあとの記録
   * @param created 新しく作ったか（上書きなら false）
   */
  public record Upserted(BodyRecord record, boolean created) {}

  /**
   * 行を新しく作ったか。PostgreSQL の {@code xmax} は、INSERT した行なら 0、ON CONFLICT DO UPDATE で更新した行なら更新した取引の ID
   * になる
   */
  private static final Field<Boolean> INSERTED = field("(xmax = 0)", SQLDataType.BOOLEAN);

  private final DSLContext dsl;

  public BodyRecordRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /**
   * その日の記録を作るか、まるごと上書きする（体脂肪率を入れなければ、体脂肪率は消える）。1日1件は UNIQUE(user_id, recorded_on)
   * で守り、同時に2回呼んでも1件になる。 上書きでは、作った日時は残す
   */
  public Upserted upsert(NewBodyRecord record, OffsetDateTime now) {
    return dsl.insertInto(BODY_RECORDS)
        .set(BODY_RECORDS.USER_ID, record.userId())
        .set(BODY_RECORDS.RECORDED_ON, record.recordedOn())
        .set(BODY_RECORDS.WEIGHT_KG, record.weightKg())
        .set(BODY_RECORDS.BODY_FAT_PCT, record.bodyFatPct())
        .set(BODY_RECORDS.CREATED_AT, now)
        .set(BODY_RECORDS.UPDATED_AT, now)
        .onConflict(BODY_RECORDS.USER_ID, BODY_RECORDS.RECORDED_ON)
        .doUpdate()
        .set(BODY_RECORDS.WEIGHT_KG, record.weightKg())
        .set(BODY_RECORDS.BODY_FAT_PCT, record.bodyFatPct())
        .set(BODY_RECORDS.UPDATED_AT, now)
        .returningResult(
            BODY_RECORDS.RECORDED_ON, BODY_RECORDS.WEIGHT_KG, BODY_RECORDS.BODY_FAT_PCT, INSERTED)
        .fetchSingle(
            r -> new Upserted(new BodyRecord(r.value1(), r.value2(), r.value3()), r.value4()));
  }

  /**
   * 一覧（A-101）。日付の新しい順に limit 件まで。
   *
   * @param before この日より前（古い）から。最初のページは null
   */
  public List<BodyRecord> findPage(long userId, LocalDate before, int limit) {
    return dsl.select(BODY_RECORDS.RECORDED_ON, BODY_RECORDS.WEIGHT_KG, BODY_RECORDS.BODY_FAT_PCT)
        .from(BODY_RECORDS)
        .where(BODY_RECORDS.USER_ID.eq(userId))
        .and(before == null ? noCondition() : BODY_RECORDS.RECORDED_ON.lt(before))
        .orderBy(BODY_RECORDS.RECORDED_ON.desc())
        .limit(limit)
        .fetch(Records.mapping(BodyRecord::new));
  }

  /** 本人のその日の記録を消す。消したら true、なければ false */
  public boolean delete(long userId, LocalDate date) {
    return dsl.deleteFrom(BODY_RECORDS)
            .where(BODY_RECORDS.USER_ID.eq(userId))
            .and(BODY_RECORDS.RECORDED_ON.eq(date))
            .execute()
        > 0;
  }

  /**
   * 推移（A-103）。日付の古い順。
   *
   * @param from 期間の始まりの日（この日を含む）
   */
  public List<BodyRecord> trend(long userId, LocalDate from) {
    return dsl.select(BODY_RECORDS.RECORDED_ON, BODY_RECORDS.WEIGHT_KG, BODY_RECORDS.BODY_FAT_PCT)
        .from(BODY_RECORDS)
        .where(BODY_RECORDS.USER_ID.eq(userId))
        .and(BODY_RECORDS.RECORDED_ON.ge(from))
        .orderBy(BODY_RECORDS.RECORDED_ON.asc())
        .fetch(Records.mapping(BodyRecord::new));
  }
}
