package com.okimoto.fitlog.exercise.calc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoverageProbeTest {
  @Test
  void 正の数は1() {
    assertThat(new CoverageProbe().sign(5)).isEqualTo(1);
  }
}
