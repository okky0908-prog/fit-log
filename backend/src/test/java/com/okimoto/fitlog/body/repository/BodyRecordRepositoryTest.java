package com.okimoto.fitlog.body.repository;

import static com.okimoto.fitlog.jooq.Tables.BODY_RECORDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.okimoto.fitlog.support.TestcontainersConfiguration;
import com.okimoto.fitlog.user.repository.NewUser;
import com.okimoto.fitlog.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jooq.test.autoconfigure.JooqTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** 体重の記録の SQL（docs/database.md「body_records」）。本人の記録だけを扱う。テストごとに新しいユーザーを作り、ロールバックする */
@JooqTest
@Import({TestcontainersConfiguration.class, BodyRecordRepository.class, UserRepository.class})
@ActiveProfiles("test")
class BodyRecordRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-20T08:00:00+09:00");

  @Autowired private BodyRecordRepository repository;
  @Autowired private UserRepository userRepository;
  @Autowired private DSLContext dsl;

  private long me;
  private long sato;

  @BeforeEach
  void setUp() {
    me = user("body_me");
    sato = user("body_sato");
  }

  @Nested
  class 記録と上書き {

    @Test
    void その日の記録がなければ作る() {
      assertThat(repository.upsert(record(me, "2026-11-20", "73.000", "18.5"), NOW))
          .isEqualTo(
              new BodyRecordRepository.Upserted(
                  new BodyRecord(day("2026-11-20"), kg("73.000"), pct("18.5")), true));
    }

    @Test
    void 同じ日にもう一度記録すると上書きし_作った日時は残す() {
      repository.upsert(record(me, "2026-11-20", "73.000", "18.5"), NOW);
      OffsetDateTime later = NOW.plusHours(3);

      // 体脂肪率を入れなければ、体脂肪率は消える（その日の記録をまるごと置きかえる）
      assertThat(repository.upsert(record(me, "2026-11-20", "72.600", null), later))
          .isEqualTo(
              new BodyRecordRepository.Upserted(
                  new BodyRecord(day("2026-11-20"), kg("72.600"), null), false));
      var row = dsl.fetchSingle(BODY_RECORDS, BODY_RECORDS.USER_ID.eq(me));
      assertThat(row.getCreatedAt()).isAtSameInstantAs(NOW);
      assertThat(row.getUpdatedAt()).isAtSameInstantAs(later);
    }

    @Test
    void 他人の同じ日の記録とは別に作る() {
      repository.upsert(record(sato, "2026-11-20", "60.000", null), NOW);

      assertThat(repository.upsert(record(me, "2026-11-20", "73.000", null), NOW).created())
          .isTrue();
      assertThat(dsl.fetchCount(BODY_RECORDS)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void DBの下限は_44lbのkgが入る19点95() {
      assertThat(repository.upsert(record(me, "2026-11-20", "19.958", null), NOW).created())
          .isTrue();
      assertThatThrownBy(() -> repository.upsert(record(me, "2026-11-21", "19.949", null), NOW))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  class 一覧 {

    @Test
    void 日付の新しい順に_指定の件数まで_カーソルの日より前から() {
      for (String date : new String[] {"2026-11-18", "2026-11-20", "2026-11-19", "2026-11-17"}) {
        repository.upsert(record(me, date, "73.000", null), NOW);
      }
      repository.upsert(record(sato, "2026-11-21", "60.000", null), NOW);

      assertThat(repository.findPage(me, null, 3))
          .extracting(BodyRecord::recordedOn)
          .containsExactly(day("2026-11-20"), day("2026-11-19"), day("2026-11-18"));
      assertThat(repository.findPage(me, day("2026-11-18"), 3))
          .extracting(BodyRecord::recordedOn)
          .containsExactly(day("2026-11-17"));
    }
  }

  @Nested
  class 削除 {

    @Test
    void 本人のその日の記録だけを消す_なければfalse() {
      repository.upsert(record(me, "2026-11-20", "73.000", null), NOW);
      repository.upsert(record(sato, "2026-11-20", "60.000", null), NOW);

      assertThat(repository.delete(me, day("2026-11-20"))).isTrue();
      assertThat(repository.delete(me, day("2026-11-20"))).isFalse();
      assertThat(dsl.fetchCount(BODY_RECORDS, BODY_RECORDS.USER_ID.eq(sato))).isEqualTo(1);
    }
  }

  @Nested
  class 推移 {

    @Test
    void 始まりの日を含み_日付の古い順() {
      for (String date : new String[] {"2026-11-20", "2026-08-20", "2026-08-19"}) {
        repository.upsert(record(me, date, "73.000", "18.5"), NOW);
      }
      repository.upsert(record(sato, "2026-11-01", "60.000", null), NOW);

      assertThat(repository.trend(me, day("2026-08-20")))
          .containsExactly(
              new BodyRecord(day("2026-08-20"), kg("73.000"), pct("18.5")),
              new BodyRecord(day("2026-11-20"), kg("73.000"), pct("18.5")));
    }
  }

  private static NewBodyRecord record(long userId, String date, String weightKg, String fat) {
    return new NewBodyRecord(userId, day(date), kg(weightKg), fat == null ? null : pct(fat));
  }

  private static LocalDate day(String value) {
    return LocalDate.parse(value);
  }

  private static BigDecimal kg(String value) {
    return new BigDecimal(value);
  }

  private static BigDecimal pct(String value) {
    return new BigDecimal(value);
  }

  private long user(String username) {
    return userRepository
        .insert(
            new NewUser(
                username,
                "表示名",
                username + "@example.com",
                "hash",
                OffsetDateTime.parse("2026-01-01T00:00:00+09:00")))
        .getId();
  }
}
