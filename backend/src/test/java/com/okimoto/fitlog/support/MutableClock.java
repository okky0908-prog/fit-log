package com.okimoto.fitlog.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 進められる時計（期限の境界を確かめるテスト用） */
public final class MutableClock extends Clock {

  private Instant now;

  public MutableClock(Instant now) {
    this.now = now;
  }

  public void set(Instant now) {
    this.now = now;
  }

  public void advance(Duration duration) {
    this.now = now.plus(duration);
  }

  @Override
  public Instant instant() {
    return now;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }
}
