package com.okimoto.fitlog.stats.calc;

import java.math.BigDecimal;

/**
 * 期間の、ランニングのセットの合計（SQL で求める）。
 *
 * @param distanceKm 距離の合計（km）。距離のあるセットがなければ 0
 * @param durationSec 時間の合計（秒。距離のないセットも含む）
 * @param runCount セットの数（1セット ＝ 1回の走行）
 * @param pacedDurationSec 距離のあるセットの、時間の合計（平均ペースに使う）
 */
public record RunningTotals(
    BigDecimal distanceKm, int durationSec, int runCount, int pacedDurationSec) {}
