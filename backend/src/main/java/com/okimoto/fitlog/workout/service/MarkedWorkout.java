package com.okimoto.fitlog.workout.service;

import com.okimoto.fitlog.workout.calc.PersonalBest;
import com.okimoto.fitlog.workout.repository.WorkoutDetail;
import java.util.List;

/**
 * 記録の詳細と、その記録の自己ベスト更新の印（F-85）。
 *
 * @param detail 記録の詳細
 * @param personalBests 自己ベストを更新した種目（どの種目・セットに印を付けるかは {@code PersonalBestRules} で選ぶ）
 */
public record MarkedWorkout(WorkoutDetail detail, List<PersonalBest> personalBests) {}
