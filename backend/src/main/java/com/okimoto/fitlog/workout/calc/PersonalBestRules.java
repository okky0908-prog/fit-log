package com.okimoto.fitlog.workout.calc;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 自己ベスト更新の印を、記録のどの種目・どのセットに付けるか（F-85、docs/test-plan.md 5.3） */
public final class PersonalBestRules {

  private PersonalBestRules() {}

  /**
   * 記録の種目の1つ。
   *
   * @param exerciseId 種目
   * @param bestWeightKg その種目の、回数が1回以上のセットの最大重量。そのようなセットがなければ null
   */
  public record Entry(long exerciseId, BigDecimal bestWeightKg) {}

  /** 詳細の種目（セットの行を持つ）の比べる値 */
  public static Entry entryOf(long exerciseId, List<StoredSet> sets) {
    int best = bestSet(sets);
    return new Entry(exerciseId, best < 0 ? null : sets.get(best).weightKg());
  }

  /** 投稿カードの種目（セットを SQL で集計した値）の比べる値。集計の「最大のセット」は、回数が1回以上のセットを先に選ぶので、それが0回なら全セットが0回 */
  public static Entry entryOf(ExerciseAggregate aggregate) {
    boolean counted = aggregate.bestReps() != null && aggregate.bestReps() >= 1;
    return new Entry(aggregate.exerciseId(), counted ? aggregate.bestWeightKg() : null);
  }

  /**
   * 自己ベストを出したセットの位置（S-12 の 🏆）。回数が1回以上で重量のあるセットのうち、一番重いセット。同じ重さなら回数が多い方、それも同じなら並び順が先の方
   * （投稿カードの「最大のセット」と同じ順。docs/test-plan.md 5.4）。
   *
   * @return セットの位置。そのようなセットがなければ -1
   */
  public static int bestSet(List<StoredSet> sets) {
    int best = -1;
    for (int i = 0; i < sets.size(); i++) {
      StoredSet set = sets.get(i);
      // 重量のあるセットには、必ず回数がある（workout_sets の CHECK 制約）
      if (set.weightKg() == null || set.reps() < 1) {
        continue;
      }
      if (best < 0 || isBetter(set, sets.get(best))) {
        best = i;
      }
    }
    return best;
  }

  /**
   * 印を付ける種目の位置。更新した種目ごとに、その種目のうち最大重量を出した最初の種目（同じ種目が2回あるときは、最大重量を出した方。同じ重さなら並び順が前の方）。
   *
   * @param entries 記録の種目（並び順のとおり。投稿カードでは先頭5種目）
   * @param personalBests 更新した種目（ほかの記録の分が入っていてもよい）
   */
  public static Set<Integer> markedExercises(
      long workoutId, List<Entry> entries, List<PersonalBest> personalBests) {
    Set<Integer> marked = new HashSet<>();
    for (PersonalBest personalBest : personalBests) {
      if (personalBest.workoutId() != workoutId) {
        continue;
      }
      for (int i = 0; i < entries.size(); i++) {
        Entry entry = entries.get(i);
        if (entry.exerciseId() == personalBest.exerciseId()
            && entry.bestWeightKg() != null
            && entry.bestWeightKg().compareTo(personalBest.maxWeightKg()) == 0) {
          marked.add(i);
          break;
        }
      }
    }
    return marked;
  }

  /** 重い方、同じなら回数が多い方。同じなら先のセット（並び順が先）のまま */
  private static boolean isBetter(StoredSet set, StoredSet current) {
    int byWeight = set.weightKg().compareTo(current.weightKg());
    return byWeight > 0 || (byWeight == 0 && set.reps() > current.reps());
  }
}
