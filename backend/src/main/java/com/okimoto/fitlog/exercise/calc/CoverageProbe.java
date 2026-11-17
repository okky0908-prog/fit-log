package com.okimoto.fitlog.exercise.calc;

/** カバレッジの基準で CI が落ちることを確かめるための、テストのない Service（確かめたら消す） */
public class CoverageProbe {

  public int sign(int value) {
    if (value < 0) {
      return -1;
    }
    return 1;
  }
}
