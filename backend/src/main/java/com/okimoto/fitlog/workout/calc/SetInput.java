package com.okimoto.fitlog.workout.calc;

import java.math.BigDecimal;

/**
 * 入力されたセット（入力した単位のまま）。整数の項目も、小数が送られたことを見分けるため BigDecimal で受け取る。
 *
 * @param weight 重量（kg か lb）
 * @param reps 回数
 * @param durationSec 時間（秒）
 * @param distance 距離（km かマイル）
 */
public record SetInput(
    BigDecimal weight, BigDecimal reps, BigDecimal durationSec, BigDecimal distance) {}
