'use strict';
/*
 * FIT LOG モックアップ：データと計算
 * - データはこのタブのメモリだけに持つ（再読み込みで初期状態に戻る）
 * - 形は docs/database.md のテーブルに合わせる。数（ナイストレーニング数・自己ベスト・集計・達成率など）は列に持たず、表示のたびに計算する
 */

/* ===================== 日付（日本時間の日付を 'YYYY-MM-DD' の文字列で扱う） ===================== */
const DOW = '日月火水木金土';
const pad = (n) => String(n).padStart(2, '0');
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parseYmd = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const todayStr = () => ymd(new Date());
const addDays = (s, n) => { const d = parseYmd(s); d.setDate(d.getDate() + n); return ymd(d); };
const addMonths = (s, n) => { const d = parseYmd(s); return ymd(new Date(d.getFullYear(), d.getMonth() + n, 1)); };
const weekStart = (s) => { const d = parseYmd(s); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return ymd(d); };
const monthStart = (s) => s.slice(0, 7) + '-01';
const monthEnd = (s) => { const d = parseYmd(s); return ymd(new Date(d.getFullYear(), d.getMonth() + 1, 0)); };
const yearStart = (s) => s.slice(0, 4) + '-01-01';
const yearEnd = (s) => s.slice(0, 4) + '-12-31';
const dowOf = (s) => DOW[parseYmd(s).getDay()];

/** 「2026/11/14（土）」。今年なら年を省く */
function fmtDate(s, withYear) {
  const d = parseYmd(s);
  const y = d.getFullYear() !== new Date().getFullYear() || withYear ? `${d.getFullYear()}/` : '';
  return `${y}${pad(d.getMonth() + 1)}/${pad(d.getDate())}（${DOW[d.getDay()]}）`;
}
const fmtMD = (s) => `${Number(s.slice(5, 7))}/${Number(s.slice(8, 10))}`;

/** 投稿日時：1時間以内は「◯分前に投稿」、24時間以内は「◯時間前に投稿」、それより前は出さない */
function postedLabel(ms) {
  const min = Math.floor((Date.now() - ms) / 60000);
  if (min < 60) return `${Math.max(1, min)}分前に投稿`;
  if (min < 24 * 60) return `${Math.floor(min / 60)}時間前に投稿`;
  return '';
}
/** コメント・通知の時刻 */
function relTime(ms) {
  const min = Math.floor((Date.now() - ms) / 60000);
  if (min < 1) return 'たった今';
  if (min < 60) return `${min}分前`;
  if (min < 24 * 60) return `${Math.floor(min / 60)}時間前`;
  if (min < 48 * 60) return '昨日';
  return fmtDate(ymd(new Date(ms)));
}

/* ===================== 単位（保存は kg・km・秒。表示と入力はユーザーの単位） ===================== */
const KG_PER_LB = 0.45359237;
const KM_PER_MI = 1.609344;
const round3 = (v) => Math.round(v * 1000) / 1000;
const trimNum = (v, digits = 2) => Number(v.toFixed(digits)).toString();

function weightUnit() { return currentUser() ? currentUser().weightUnit : 'kg'; }
function distanceUnit() { return currentUser() ? currentUser().distanceUnit : 'km'; }

/** 入力の重さ（ユーザーの単位）→ kg（小数第3位、HALF_UP） */
const toKg = (v, unit = weightUnit()) => round3(unit === 'lb' ? v * KG_PER_LB : v);
const toKm = (v, unit = distanceUnit()) => round3(unit === 'mi' ? v * KM_PER_MI : v);
/** kg → 表示の値（lb は 0.5 単位、kg は 0.25 単位に丸める） */
function weightValue(kg, unit = weightUnit()) {
  return unit === 'lb' ? Math.round((kg / KG_PER_LB) * 2) / 2 : Math.round(kg * 4) / 4;
}
const fmtWeight = (kg, unit = weightUnit()) => `${trimNum(weightValue(kg, unit))}${unit}`;
const fmtWeightNum = (kg, unit = weightUnit()) => trimNum(weightValue(kg, unit)).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
/** 体重は lb なら 0.2 単位、kg なら 0.1 単位 */
function bodyValue(kg, unit = weightUnit()) {
  return unit === 'lb' ? Math.round((kg / KG_PER_LB) * 5) / 5 : Math.round(kg * 10) / 10;
}
const fmtBody = (kg, unit = weightUnit()) => `${bodyValue(kg, unit).toFixed(1)}${unit}`;
const distValue = (km, unit = distanceUnit()) => (unit === 'mi' ? km / KM_PER_MI : km);
const fmtDist = (km, unit = distanceUnit()) => `${distValue(km, unit).toFixed(2)}${unit === 'mi' ? 'mi' : 'km'}`;
function fmtDuration(sec) {
  sec = Math.round(sec);
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
}
function fmtPace(secPerKm, unit = distanceUnit()) {
  if (secPerKm == null || !isFinite(secPerKm)) return '—';
  const v = unit === 'mi' ? secPerKm * KM_PER_MI : secPerKm;
  return `${fmtDuration(v)}/${unit === 'mi' ? 'mi' : 'km'}`;
}
const fmtInt = (v) => Math.round(v).toLocaleString('ja-JP');

/* ===================== 乱数（初期データを毎回同じにするため） ===================== */
let seedState = 20261116;
function rnd() {
  seedState |= 0; seedState = (seedState + 0x6d2b79f5) | 0;
  let t = Math.imul(seedState ^ (seedState >>> 15), 1 | seedState);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];

/* ===================== 種目の初期データ（docs/database.md と同じ58種目） ===================== */
const BODY_PARTS = [
  ['chest', '胸'], ['back', '背中'], ['legs', '脚'], ['shoulders', '肩'],
  ['arms', '腕'], ['abs', '腹'], ['full_body', '全身'], ['cardio', '有酸素'],
];
const BODY_PART_LABEL = Object.fromEntries(BODY_PARTS);
const RECORD_TYPE_LABEL = { W: '重量 × 回数', R: '回数のみ', T: '時間・距離' };
const EXERCISE_SEED = [
  ['chest', 'ベンチプレス', 'W', 6.0], ['chest', 'インクラインベンチプレス', 'W', 6.0], ['chest', 'ダンベルプレス', 'W', 3.5],
  ['chest', 'ダンベルフライ', 'W', 3.5], ['chest', 'チェストプレス', 'W', 3.5], ['chest', 'ケーブルクロスオーバー', 'W', 3.5],
  ['chest', 'ディップス', 'R', 3.8], ['chest', '腕立て伏せ', 'R', 3.8],
  ['back', 'デッドリフト', 'W', 6.0], ['back', 'ベントオーバーロウ', 'W', 6.0], ['back', 'ラットプルダウン', 'W', 3.5],
  ['back', 'シーテッドロウ', 'W', 3.5], ['back', 'ワンハンドダンベルロウ', 'W', 3.5], ['back', '懸垂', 'R', 3.8],
  ['back', 'バックエクステンション', 'R', 3.5],
  ['legs', 'スクワット', 'W', 6.0], ['legs', 'フロントスクワット', 'W', 6.0], ['legs', 'ルーマニアンデッドリフト', 'W', 6.0],
  ['legs', 'レッグプレス', 'W', 3.5], ['legs', 'レッグエクステンション', 'W', 3.5], ['legs', 'レッグカール', 'W', 3.5],
  ['legs', 'ランジ', 'W', 3.5], ['legs', 'ヒップスラスト', 'W', 3.5], ['legs', 'カーフレイズ', 'W', 3.5],
  ['shoulders', 'ショルダープレス', 'W', 6.0], ['shoulders', 'ダンベルショルダープレス', 'W', 3.5], ['shoulders', 'サイドレイズ', 'W', 3.5],
  ['shoulders', 'フロントレイズ', 'W', 3.5], ['shoulders', 'リアレイズ', 'W', 3.5], ['shoulders', 'アップライトロウ', 'W', 3.5],
  ['shoulders', 'シュラッグ', 'W', 3.5],
  ['arms', 'バーベルカール', 'W', 3.5], ['arms', 'ダンベルカール', 'W', 3.5], ['arms', 'ハンマーカール', 'W', 3.5],
  ['arms', 'ナローベンチプレス', 'W', 6.0], ['arms', 'トライセプスエクステンション', 'W', 3.5], ['arms', 'トライセプスプッシュダウン', 'W', 3.5],
  ['abs', 'クランチ', 'R', 2.8], ['abs', 'レッグレイズ', 'R', 2.8], ['abs', 'アブローラー', 'R', 3.8], ['abs', 'ケーブルクランチ', 'W', 3.5],
  ['abs', 'プランク', 'T', 2.8],
  ['full_body', 'パワークリーン', 'W', 6.0], ['full_body', 'ケトルベルスイング', 'W', 8.0], ['full_body', 'バーピー', 'R', 8.0],
  ['full_body', 'サーキットトレーニング', 'T', 8.0], ['full_body', 'ヨガ', 'T', 2.5],
  ['cardio', 'ランニング', 'T', 8.3, true], ['cardio', 'ジョギング', 'T', 7.0, true], ['cardio', 'トレッドミル', 'T', 8.3, true],
  ['cardio', 'ウォーキング', 'T', 3.5], ['cardio', 'エアロバイク', 'T', 6.8], ['cardio', 'サイクリング', 'T', 7.5],
  ['cardio', '水泳', 'T', 6.0], ['cardio', 'ローイングマシン', 'T', 7.0], ['cardio', 'クロストレーナー', 'T', 5.0],
  ['cardio', 'ステアクライマー', 'T', 9.0], ['cardio', '縄跳び', 'T', 11.8],
];
/** ランニングの速さ（km/時）ごとの METs（running_mets） */
const RUNNING_METS = [[0, 6.0], [8.0, 8.3], [8.4, 9.0], [9.7, 9.8], [10.8, 10.5], [11.3, 11.0], [12.1, 11.5], [12.9, 11.8],
  [13.8, 12.3], [14.5, 12.8], [16.1, 14.5], [17.7, 16.0], [19.3, 19.0], [20.9, 19.8], [22.5, 23.0]];
/** 自分専用の種目の標準の METs */
function defaultMets(recordType, bodyPart) {
  if (recordType === 'W') return 3.5;
  if (recordType === 'R') return 3.8;
  return bodyPart === 'cardio' ? 6.0 : 3.5;
}

/* ===================== DB（メモリ） ===================== */
const db = {
  users: [], exercises: [], workouts: [], comments: [], likes: [], follows: [], notifications: [],
  bodyRecords: [], goals: [], goalAchievements: [], goalCheers: [], templates: [], mails: [],
};
const seq = {};
const nextId = (table) => (seq[table] = (seq[table] || 0) + 1);

const session = { meId: null };
const currentUser = () => (session.meId ? userById(session.meId) : null);
const userById = (id) => db.users.find((u) => u.id === id);
const userByName = (name) => db.users.find((u) => u.username.toLowerCase() === String(name).toLowerCase());
const exerciseById = (id) => db.exercises.find((e) => e.id === id);
const exerciseByName = (name) => db.exercises.find((e) => e.name === name && e.ownerId == null);
const workoutById = (id) => db.workouts.find((w) => w.id === id);
const goalById = (id) => db.goals.find((g) => g.id === id);

/* ===================== 閲覧の制御（docs/database.md の閲覧の制御の条件） ===================== */
const followRow = (followerId, followeeId) => db.follows.find((f) => f.followerId === followerId && f.followeeId === followeeId);
const isAccepted = (followerId, followeeId) => followRow(followerId, followeeId)?.status === 'accepted';
/** 自分から相手へのフォローの状態：none / requested / following */
function followStatus(meId, otherId) {
  const f = followRow(meId, otherId);
  if (!f) return 'none';
  return f.status === 'accepted' ? 'following' : 'requested';
}
/** 投稿を見てよいか */
function canViewWorkout(w, viewerId) {
  if (!w) return false;
  if (w.userId === viewerId) return true;
  const owner = userById(w.userId);
  const follower = isAccepted(viewerId, w.userId);
  if (w.visibility === 'public') return !owner.isPrivate || follower;
  if (w.visibility === 'followers') return follower;
  return false;
}
/** 公開した目標を見てよいか（体重の目標は本人だけ） */
function canViewGoal(g, viewerId) {
  if (!g) return false;
  if (g.userId === viewerId) return true;
  if (g.goalType === 'body_weight') return false;
  const owner = userById(g.userId);
  const follower = isAccepted(viewerId, g.userId);
  if (g.visibility === 'public') return !owner.isPrivate || follower;
  if (g.visibility === 'followers') return follower;
  return false;
}
/** 鍵アカウントの記録・フォロー一覧を見られるか */
const canViewAccount = (owner, viewerId) => !owner.isPrivate || owner.id === viewerId || isAccepted(viewerId, owner.id);

/* ===================== 文字 ===================== */
/** 文字数（前後の空白を除き、コードポイントで数える。絵文字も1文字） */
const charCount = (s) => [...String(s || '').trim()].length;
const normalize = (s) => String(s || '').normalize('NFKC').toLowerCase();
const HASHTAG_RE = /(^|[^\p{L}\p{N}_ー])[#＃]([\p{L}\p{N}_ー]+)/gu;
/** ハッシュタグの取り出し：1〜30文字。31文字以上は扱わない。1投稿10個まで */
function extractHashtags(text) {
  const tags = [];
  for (const m of String(text || '').matchAll(HASHTAG_RE)) {
    if ([...m[2]].length > 30) continue;
    const t = normalize(m[2]);
    if (!tags.includes(t)) tags.push(t);
    if (tags.length >= 10) break;
  }
  return tags;
}

/* ===================== 記録の計算 ===================== */
const recordTypeOf = (ex) => exerciseById(ex.exerciseId).recordType;
/** 「前の記録」：同じ人の、実施日が前か、同じ実施日で先に投稿した記録 */
const isBefore = (a, b) => a.performedOn < b.performedOn || (a.performedOn === b.performedOn && a.createdAt < b.createdAt);
/** 最大のセット：回数が1回以上の中で一番重い（同じなら回数が多い→並び順が先）。全部0回なら一番重い */
function maxSetIndex(sets) {
  const lifted = sets.map((s, i) => [s, i]).filter(([s]) => s.reps >= 1);
  const pool = lifted.length ? lifted : sets.map((s, i) => [s, i]);
  let best = pool[0];
  for (const cur of pool) {
    if (cur[0].weightKg > best[0].weightKg || (cur[0].weightKg === best[0].weightKg && cur[0].reps > best[0].reps)) best = cur;
  }
  return best[1];
}
/** その種目の、回数が1回以上のセットの最大重量 */
function maxLiftedKg(sets) {
  const v = sets.filter((s) => s.reps >= 1).map((s) => s.weightKg);
  return v.length ? Math.max(...v) : null;
}

/** 自己ベスト更新の印（F-85）。{ exercises: Set<種目の位置>, sets: Set<'種目の位置-セットの位置'> } */
function personalBests(w) {
  const result = { exercises: new Set(), sets: new Set() };
  const earlier = db.workouts.filter((o) => o.userId === w.userId && o.id !== w.id && isBefore(o, w));
  const ids = [...new Set(w.exercises.filter((e) => recordTypeOf(e) === 'W').map((e) => e.exerciseId))];
  for (const exId of ids) {
    const prevSets = earlier.flatMap((o) => o.exercises.filter((e) => e.exerciseId === exId).flatMap((e) => e.sets));
    const prevMax = maxLiftedKg(prevSets);
    if (prevMax == null) continue; // 初めての種目には付けない
    const occurrences = w.exercises.map((e, i) => [e, i]).filter(([e]) => e.exerciseId === exId);
    const curMax = maxLiftedKg(occurrences.flatMap(([e]) => e.sets));
    if (curMax == null || curMax <= prevMax) continue;
    const [ex, exIdx] = occurrences.find(([e]) => maxLiftedKg(e.sets) === curMax);
    result.exercises.add(exIdx);
    result.sets.add(`${exIdx}-${maxSetIndex(ex.sets)}`);
  }
  return result;
}

/** 種目の要約の文言（WorkoutCard） */
function exerciseSummary(ex) {
  const e = exerciseById(ex.exerciseId);
  const sets = ex.sets;
  if (e.recordType === 'W') {
    const uniform = sets.every((s) => s.weightKg === sets[0].weightKg && s.reps === sets[0].reps);
    if (uniform) return `${e.name} ${fmtWeight(sets[0].weightKg)} × ${sets[0].reps}回 × ${sets.length}セット`;
    const m = sets[maxSetIndex(sets)];
    return `${e.name} ${sets.length}セット（最大 ${fmtWeight(m.weightKg)} × ${m.reps}回）`;
  }
  if (e.recordType === 'R') {
    const uniform = sets.every((s) => s.reps === sets[0].reps);
    if (uniform) return `${e.name} ${sets[0].reps}回 × ${sets.length}セット`;
    return `${e.name} 合計 ${sets.reduce((a, s) => a + s.reps, 0)}回（${sets.length}セット）`;
  }
  const dur = sets.reduce((a, s) => a + s.durationSec, 0);
  const withDist = sets.filter((s) => s.distanceKm != null);
  const dist = withDist.reduce((a, s) => a + s.distanceKm, 0);
  if (sets.length === 1) {
    const s = sets[0];
    if (s.distanceKm == null) return `${e.name} ${fmtDuration(s.durationSec)}`;
    return `${e.name} ${fmtDist(s.distanceKm)} ${fmtDuration(s.durationSec)}（${fmtPace(s.durationSec / s.distanceKm)}）`;
  }
  return `${e.name} 合計 ${withDist.length ? fmtDist(dist) + ' ' : ''}${fmtDuration(dur)}（${sets.length}回）`;
}

/** 実施日以前で一番近い体重の記録 */
function bodyRecordOnOrBefore(userId, date) {
  return db.bodyRecords.filter((b) => b.userId === userId && b.recordedOn <= date).sort((a, b) => (a.recordedOn < b.recordedOn ? 1 : -1))[0] || null;
}
const runningMets = (speedKmh) => RUNNING_METS.filter(([min]) => min <= speedKmh).pop()[1];

/** 消費カロリーの目安（F-86） */
function calories(w) {
  const body = bodyRecordOnOrBefore(w.userId, w.performedOn);
  if (!body) return { status: 'no_body_record' };
  const kg = body.weightKg;
  let aerobic = 0, aerobicSec = 0, strengthSets = 0, strengthMetsSum = 0;
  for (const ex of w.exercises) {
    const e = exerciseById(ex.exerciseId);
    if (e.recordType === 'T') {
      for (const s of ex.sets) {
        const mets = e.isRunning && s.distanceKm ? runningMets(s.distanceKm / (s.durationSec / 3600)) : e.mets;
        aerobic += mets * kg * (s.durationSec / 3600) * 1.05;
        aerobicSec += s.durationSec;
      }
    } else {
      strengthSets += ex.sets.length;
      strengthMetsSum += e.mets * ex.sets.length;
    }
  }
  let strength = 0;
  const included = w.durationMin != null && strengthSets > 0;
  if (included) {
    const sec = w.durationMin * 60 - aerobicSec;
    if (sec > 0) strength = (strengthMetsSum / strengthSets) * kg * (sec / 3600) * 1.05;
  }
  return {
    status: 'ok', totalKcal: Math.round(aerobic + strength), aerobicKcal: Math.round(aerobic), strengthKcal: Math.round(strength),
    strengthIncluded: included, bodyWeightRecordedOn: body.recordedOn,
  };
}

/** 期間の集計（F-80・F-81） */
function periodStats(userId, from, to) {
  const ws = db.workouts.filter((w) => w.userId === userId && w.performedOn >= from && w.performedOn <= to);
  const days = new Set(ws.map((w) => w.performedOn));
  let volume = 0, durationSec = 0, distance = 0, kcal = 0, kcalCount = 0, excluded = 0;
  const parts = {};
  for (const w of ws) {
    for (const ex of w.exercises) {
      const e = exerciseById(ex.exerciseId);
      parts[e.bodyPart] = (parts[e.bodyPart] || 0) + ex.sets.length;
      for (const s of ex.sets) {
        if (e.recordType === 'W') volume += s.weightKg * s.reps;
        if (e.recordType === 'T') { durationSec += s.durationSec; distance += s.distanceKm || 0; }
      }
    }
    const c = calories(w);
    if (c.status === 'ok') { kcal += c.totalKcal; kcalCount++; } else excluded++;
  }
  return { workouts: ws, trainingDays: days.size, volume, durationSec, distance, kcal: kcalCount ? kcal : null, excluded, parts };
}
function dailyValues(userId, from, to, metric) {
  const out = [];
  for (let d = from; d <= to; d = addDays(d, 1)) {
    const st = periodStats(userId, d, d);
    out.push({ date: d, value: metric === 'volume' ? st.volume : st.durationSec / 60 });
  }
  return out;
}

/** ランニングの集計（F-83） */
function runningStats(userId, from, to) {
  const rows = [];
  for (const w of db.workouts.filter((x) => x.userId === userId && x.performedOn >= from && x.performedOn <= to)) {
    for (const ex of w.exercises) {
      if (!exerciseById(ex.exerciseId).isRunning) continue;
      for (const s of ex.sets) rows.push({ ...s, workout: w });
    }
  }
  const withDist = rows.filter((r) => r.distanceKm != null);
  const distance = withDist.reduce((a, r) => a + r.distanceKm, 0);
  const durationSec = rows.reduce((a, r) => a + r.durationSec, 0);
  const avgPace = distance > 0 ? withDist.reduce((a, r) => a + r.durationSec, 0) / distance : null;
  const fastest = withDist.filter((r) => r.distanceKm >= 1)
    .sort((a, b) => a.durationSec / a.distanceKm - b.durationSec / b.distanceKm || b.distanceKm - a.distanceKm || (a.workout.performedOn < b.workout.performedOn ? 1 : -1))[0] || null;
  const longest = [...withDist]
    .sort((a, b) => b.distanceKm - a.distanceKm || a.durationSec - b.durationSec || (a.workout.performedOn < b.workout.performedOn ? 1 : -1))[0] || null;
  return { count: rows.length, distance, durationSec, avgPace, fastest, longest };
}

/** 種目ごとの推移（F-84） */
function exerciseTrend(userId, exerciseId, from) {
  const e = exerciseById(exerciseId);
  const byDate = {};
  for (const w of db.workouts.filter((x) => x.userId === userId && (!from || x.performedOn >= from))) {
    for (const ex of w.exercises.filter((x) => x.exerciseId === exerciseId)) {
      (byDate[w.performedOn] ||= []).push({ w, sets: ex.sets });
    }
  }
  return Object.keys(byDate).sort().map((date) => {
    const entries = byDate[date];
    const sets = entries.flatMap((x) => x.sets);
    const p = { date, workoutId: entries[0].w.id };
    if (e.recordType === 'W') {
      const lifted = sets.filter((s) => s.reps >= 1);
      p.maxWeightKg = lifted.length ? Math.max(...lifted.map((s) => s.weightKg)) : null;
      const top = lifted.find((s) => s.weightKg === p.maxWeightKg);
      p.maxWeightReps = top ? top.reps : null;
      const e1 = sets.filter((s) => s.reps >= 1 && s.reps <= 10).map((s) => (s.reps === 1 ? s.weightKg : round3(s.weightKg * (1 + s.reps / 30))));
      p.maxE1rmKg = e1.length ? Math.max(...e1) : null;
      const best = entries.find((x) => x.sets.some((s) => s.weightKg === p.maxWeightKg && s.reps >= 1));
      if (best) p.workoutId = best.w.id;
    } else if (e.recordType === 'R') {
      p.totalReps = sets.reduce((a, s) => a + s.reps, 0);
      p.maxReps = Math.max(...sets.map((s) => s.reps));
    } else {
      p.durationSec = sets.reduce((a, s) => a + s.durationSec, 0);
      const wd = sets.filter((s) => s.distanceKm != null);
      p.distanceKm = wd.length ? wd.reduce((a, s) => a + s.distanceKm, 0) : null;
      p.pace = e.isRunning && p.distanceKm ? wd.reduce((a, s) => a + s.durationSec, 0) / p.distanceKm : null;
    }
    return p;
  });
}
/** 自己ベスト（全期間の最大重量） */
function personalBestOf(userId, exerciseId) {
  let best = null;
  for (const w of db.workouts.filter((x) => x.userId === userId)) {
    for (const ex of w.exercises.filter((x) => x.exerciseId === exerciseId)) {
      for (const s of ex.sets) {
        if (s.reps >= 1 && (!best || s.weightKg > best.weightKg)) best = { weightKg: s.weightKg, performedOn: w.performedOn, workoutId: w.id };
      }
    }
  }
  return best;
}

/* ===================== 目標（F-100〜F-107） ===================== */
const GOAL_TYPE_LABEL = { frequency: '頻度', volume: '量', weight: '重量', body_weight: '体重' };
const METRIC_LABEL = { volume: '総挙上量', duration: '運動時間', distance: '距離' };
const periodStartOf = (g, date) => (g.period === 'week' ? weekStart(date) : monthStart(date));
const periodEndOf = (g, start) => (g.period === 'week' ? addDays(start, 6) : monthEnd(start));
const isExpired = (g) => g.deadline && g.deadline < todayStr() && !g.achievedOn;

function bodyGoalWeights(g) {
  const recs = db.bodyRecords.filter((b) => b.userId === g.userId).sort((a, b) => (a.recordedOn < b.recordedOn ? -1 : 1));
  if (!recs.length) return null;
  const before = recs.filter((b) => b.recordedOn <= g.startedOn);
  const start = before.length ? before[before.length - 1] : recs.find((b) => b.recordedOn > g.startedOn);
  return { start, current: recs[recs.length - 1] };
}
/** 目標の達成率（0〜1 を超えることもある生の値）と表示用の値 */
function goalProgress(g, date = todayStr()) {
  if (g.goalType === 'frequency' || g.goalType === 'volume') {
    const from = periodStartOf(g, date), to = periodEndOf(g, from);
    const ws = db.workouts.filter((w) => w.userId === g.userId && w.performedOn >= from && w.performedOn <= to);
    if (g.goalType === 'frequency') {
      const n = new Set(ws.map((w) => w.performedOn)).size;
      return { raw: n / g.targetValue, current: n, label: `${n}/${g.targetValue}日`, periodStart: from };
    }
    let sum = 0;
    for (const w of ws) for (const ex of w.exercises) {
      const e = exerciseById(ex.exerciseId);
      if (g.exerciseId && ex.exerciseId !== g.exerciseId) continue;
      if (g.bodyPart && e.bodyPart !== g.bodyPart) continue;
      for (const s of ex.sets) {
        if (g.metric === 'volume' && e.recordType === 'W') sum += s.weightKg * s.reps;
        if (g.metric === 'duration' && e.recordType === 'T') sum += s.durationSec / 60;
        if (g.metric === 'distance' && e.recordType === 'T') sum += s.distanceKm || 0;
      }
    }
    return { raw: sum / g.targetValue, current: sum, label: `${goalValueText(g, sum, false)}/${goalValueText(g, g.targetValue)}`, periodStart: from };
  }
  if (g.goalType === 'weight') {
    const pb = personalBestOf(g.userId, g.exerciseId);
    const cur = pb ? pb.weightKg : 0;
    return { raw: cur / g.targetValue, current: cur, label: `${fmtWeightNum(cur)}/${fmtWeight(g.targetValue)}` };
  }
  const bw = bodyGoalWeights(g);
  if (!bw) return { raw: 0, current: null, label: '体重の記録がありません' };
  const s = bw.start.weightKg, c = bw.current.weightKg;
  const raw = s === g.targetValue ? 1 : (s - c) / (s - g.targetValue); // 維持する目標は 100%
  return { raw, current: c, start: bw.start, label: `開始 ${fmtBody(s)} → 今 ${fmtBody(c)}` };
}
const ratePercent = (raw) => Math.max(0, Math.min(100, Math.floor(raw * 100 + 1e-9)));
function goalValueText(g, v, withUnit = true) {
  if (g.goalType === 'frequency') return `${v}${withUnit ? '日' : ''}`;
  if (g.goalType === 'weight') return withUnit ? fmtWeight(v) : fmtWeightNum(v);
  if (g.goalType === 'body_weight') return fmtBody(v);
  if (g.metric === 'volume') return `${fmtWeightNum(v)}${withUnit ? weightUnit() : ''}`;
  if (g.metric === 'duration') return `${Math.round(v)}${withUnit ? '分' : ''}`;
  const d = distValue(v);
  return `${Number.isInteger(d) ? d : d.toFixed(1)}${withUnit ? (distanceUnit() === 'mi' ? 'mi' : 'km') : ''}`;
}
/** 目標の名前を自動で作る */
function autoGoalName(f) {
  const per = f.period === 'month' ? '月間' : '週';
  if (f.goalType === 'frequency') return f.period === 'month' ? `月${f.targetValue || '?'}日トレーニング` : `週${f.targetValue || '?'}日トレーニング`;
  if (f.goalType === 'volume') {
    const target = f.exerciseId ? exerciseById(f.exerciseId).name : f.bodyPart ? BODY_PART_LABEL[f.bodyPart] : '';
    const unit = f.metric === 'volume' ? weightUnit() : f.metric === 'duration' ? '分' : distanceUnit() === 'mi' ? 'mi' : 'km';
    const what = f.metric === 'distance' ? (target || 'ランニング') : `${target}${METRIC_LABEL[f.metric]}`;
    return `${f.period === 'month' ? '月間' : '週間'}${what} ${f.targetValue || '?'}${unit}`.replace(per + per, per);
  }
  if (f.goalType === 'weight') return `${f.exerciseId ? exerciseById(f.exerciseId).name : '種目'} ${f.targetValue || '?'}${weightUnit()}`;
  return `体重 ${f.targetValue || '?'}${weightUnit()}`;
}

/** 達成の判定（保存の確定後に動く。F-104） */
function judgeGoals(userId, { notify = true } = {}) {
  const today = todayStr();
  for (const g of db.goals.filter((x) => x.userId === userId)) {
    if (g.goalType === 'weight' || g.goalType === 'body_weight') {
      if (g.achievedOn) continue;
      if (g.deadline && g.deadline < today) continue; // 期限切れの目標は達成にしない
      if (goalProgress(g).raw >= 1) {
        g.achievedOn = today;
        if (notify) addNotification({ recipientId: userId, actorId: null, type: 'goal_achieved', goalId: g.id });
      }
    } else {
      for (let p = periodStartOf(g, g.startedOn); p <= today; p = g.period === 'week' ? addDays(p, 7) : addMonths(p, 1)) {
        if (db.goalAchievements.some((a) => a.goalId === g.id && a.periodStart === p)) continue;
        if (goalProgress(g, p).raw >= 1) {
          db.goalAchievements.push({ goalId: g.id, periodStart: p, achievedOn: today });
          if (notify) addNotification({ recipientId: userId, actorId: null, type: 'goal_achieved', goalId: g.id, periodStart: p });
        }
      }
    }
  }
}
function goalHistory(g) {
  const periods = [];
  for (let p = periodStartOf(g, g.startedOn); p <= todayStr(); p = g.period === 'week' ? addDays(p, 7) : addMonths(p, 1)) periods.push(p);
  const achieved = db.goalAchievements.filter((a) => a.goalId === g.id).sort((a, b) => (a.periodStart < b.periodStart ? 1 : -1));
  return { achieved, total: periods.length };
}

/* ===================== 通知（F-110） ===================== */
function addNotification(n) {
  if (n.actorId != null && n.actorId === n.recipientId) return; // 自分の操作で自分に通知しない
  db.notifications.push({ id: nextId('notifications'), createdAt: Date.now(), readAt: null, ...n });
}
function removeNotifications(pred) { db.notifications = db.notifications.filter((n) => !pred(n)); }
/** 見られなくなった投稿・コメントの通知は出さない */
function visibleNotifications(userId) {
  return db.notifications
    .filter((n) => n.recipientId === userId)
    .filter((n) => !n.workoutId || canViewWorkout(workoutById(n.workoutId), userId))
    .filter((n) => !n.actorId || userById(n.actorId))
    .sort((a, b) => b.createdAt - a.createdAt || b.id - a.id);
}
const unreadCount = (userId) => visibleNotifications(userId).filter((n) => !n.readAt).length;

/* ===================== 操作（API の代わり） ===================== */
function toggleLike(workoutId, userId, at = Date.now()) {
  const w = workoutById(workoutId);
  const i = db.likes.findIndex((l) => l.workoutId === workoutId && l.userId === userId);
  if (i >= 0) {
    db.likes.splice(i, 1);
    removeNotifications((n) => n.type === 'like' && n.workoutId === workoutId && n.actorId === userId);
    return false;
  }
  db.likes.push({ workoutId, userId, createdAt: at });
  addNotification({ recipientId: w.userId, actorId: userId, type: 'like', workoutId, createdAt: at });
  return true;
}
/** コメント・返信。返信は返信元（1段目）にぶら下げる */
function addComment(workoutId, userId, content, parentId = null, at = Date.now()) {
  const w = workoutById(workoutId);
  const c = { id: nextId('comments'), workoutId, userId, parentId, content, createdAt: at };
  db.comments.push(c);
  if (parentId) {
    const parent = db.comments.find((x) => x.id === parentId);
    if (parent.userId !== userId) addNotification({ recipientId: parent.userId, actorId: userId, type: 'reply', workoutId, commentId: c.id, createdAt: at });
    if (w.userId !== parent.userId) addNotification({ recipientId: w.userId, actorId: userId, type: 'comment', workoutId, commentId: c.id, createdAt: at });
  } else {
    addNotification({ recipientId: w.userId, actorId: userId, type: 'comment', workoutId, commentId: c.id, createdAt: at });
  }
  return c;
}
function deleteComment(id) {
  const ids = db.comments.filter((c) => c.id === id || c.parentId === id).map((c) => c.id);
  db.comments = db.comments.filter((c) => !ids.includes(c.id));
  removeNotifications((n) => ids.includes(n.commentId));
}
function deleteWorkout(id) {
  const cids = db.comments.filter((c) => c.workoutId === id).map((c) => c.id);
  db.workouts = db.workouts.filter((w) => w.id !== id);
  db.comments = db.comments.filter((c) => c.workoutId !== id);
  db.likes = db.likes.filter((l) => l.workoutId !== id);
  removeNotifications((n) => n.workoutId === id || cids.includes(n.commentId));
}
/** フォロー（鍵アカウントならリクエスト）。何回呼んでも同じ結果 */
function follow(meId, otherId, at = Date.now()) {
  if (followRow(meId, otherId)) return;
  const other = userById(otherId);
  const status = other.isPrivate ? 'pending' : 'accepted';
  db.follows.push({ followerId: meId, followeeId: otherId, status, createdAt: at });
  addNotification({ recipientId: otherId, actorId: meId, type: status === 'pending' ? 'follow_request' : 'follow', createdAt: at });
}
function unfollow(meId, otherId) {
  db.follows = db.follows.filter((f) => !(f.followerId === meId && f.followeeId === otherId));
  removeNotifications((n) => n.actorId === meId && n.recipientId === otherId && (n.type === 'follow' || n.type === 'follow_request'));
}
function approveRequest(meId, requesterId) {
  const f = followRow(requesterId, meId);
  if (!f || f.status !== 'pending') return false;
  f.status = 'accepted';
  addNotification({ recipientId: requesterId, actorId: meId, type: 'follow_accepted' });
  removeNotifications((n) => n.type === 'follow_request' && n.actorId === requesterId && n.recipientId === meId);
  return true;
}
function rejectRequest(meId, requesterId) {
  db.follows = db.follows.filter((f) => !(f.followerId === requesterId && f.followeeId === meId && f.status === 'pending'));
  removeNotifications((n) => n.type === 'follow_request' && n.actorId === requesterId && n.recipientId === meId);
}
const pendingRequests = (meId) => db.follows.filter((f) => f.followeeId === meId && f.status === 'pending').sort((a, b) => b.createdAt - a.createdAt);
function toggleCheer(goalId, userId) {
  const g = goalById(goalId);
  const i = db.goalCheers.findIndex((c) => c.goalId === goalId && c.userId === userId);
  if (i >= 0) {
    db.goalCheers.splice(i, 1);
    removeNotifications((n) => n.type === 'goal_cheer' && n.goalId === goalId && n.actorId === userId);
    return false;
  }
  db.goalCheers.push({ goalId, userId, createdAt: Date.now() });
  addNotification({ recipientId: g.userId, actorId: userId, type: 'goal_cheer', goalId });
  return true;
}
/** メール（モック）。同じアドレスに1時間で5通まで */
function sendMail(to, subject, body, link) {
  const hourAgo = Date.now() - 3600 * 1000;
  if (db.mails.filter((m) => m.to === to && m.sentAt > hourAgo).length >= 5) return false;
  db.mails.unshift({ id: nextId('mails'), to, subject, body, link, sentAt: Date.now() });
  return true;
}
const emailTokens = {}; // token → { userId, email }
const resetTokens = {}; // token → { userId, expiresAt, used }
function issueVerifyMail(user, email = user.email) {
  for (const t of Object.keys(emailTokens)) if (emailTokens[t].userId === user.id) delete emailTokens[t];
  const token = 'v' + Math.random().toString(36).slice(2, 10);
  emailTokens[token] = { userId: user.id, email, expiresAt: Date.now() + 24 * 3600 * 1000 };
  const changing = email !== user.email;
  return sendMail(email, changing ? '【FIT LOG】メールアドレスの変更の確認' : '【FIT LOG】メールアドレスの確認',
    changing ? '下のリンクを開くと、メールアドレスが変更されます（24時間有効）。' : '下のリンクを開いて、メールアドレスの確認を済ませてください（24時間有効）。',
    `#/verify-email?token=${token}`);
}
const pendingEmailChange = (userId) => Object.values(emailTokens).find((t) => t.userId === userId && t.email !== userById(userId).email);

/* ===================== 画像（SVG をその場で作る） ===================== */
function svgImage(label, hue) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">` +
    `<stop offset="0" stop-color="hsl(${hue},55%,45%)"/><stop offset="1" stop-color="hsl(${(hue + 50) % 360},60%,30%)"/></linearGradient></defs>` +
    `<rect width="800" height="600" fill="url(#g)"/><text x="400" y="320" font-size="56" text-anchor="middle" fill="#fff" font-family="sans-serif">${label}</text></svg>`;
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}
function avatarImage(char, hue) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="96" height="96"><rect width="96" height="96" fill="hsl(${hue},45%,50%)"/>` +
    `<text x="48" y="62" font-size="44" text-anchor="middle" fill="#fff" font-family="sans-serif">${char}</text></svg>`;
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}

/* ===================== 初期データ ===================== */
function seed() {
  const today = todayStr();
  // 今日の分は、今より前の時刻に収める（未来の投稿日時にしない）
  const daysAgoMs = (n, h = 20, m = 0) => {
    const d = parseYmd(addDays(today, -n)); d.setHours(h, m);
    return Math.min(d.getTime(), Date.now() - (30 + ((h * 60 + m) % 300)) * 60000);
  };

  const users = [
    ['yamada', '山田太郎', 'yamada@example.com', '週4で筋トレしています。ベンチ100kgが目標！', false, 210, 400],
    ['sato', '佐藤花子', 'sato@example.com', 'ヨガと下半身トレ。記録はフォロワーさんだけに公開しています', true, 330, 380],
    ['suzuki', '鈴木一郎', 'suzuki@example.com', 'ランニング好きです。月100km', false, 120, 360],
    ['tanaka', '田中ゆき', 'tanaka@example.com', '筋トレ初心者です。よろしくお願いします', false, 20, 30],
    ['kobayashi', '小林健', 'kobayashi@example.com', 'パワーリフティング', true, 270, 200],
    ['ito', '伊藤さくら', 'ito@example.com', '最近ジムに通い始めました', false, 300, 2],
  ];
  for (const [username, displayName, email, bio, isPrivate, hue, joined] of users) {
    db.users.push({
      id: nextId('users'), username, displayName, email, password: 'password123', bio, isPrivate,
      iconUrl: avatarImage(displayName[0], hue), weightUnit: 'kg', distanceUnit: 'km',
      emailVerified: username !== 'tanaka', createdAt: daysAgoMs(joined, 10),
    });
  }
  const U = Object.fromEntries(db.users.map((u) => [u.username, u.id]));

  for (const [part, name, type, mets, running] of EXERCISE_SEED) {
    db.exercises.push({ id: nextId('exercises'), name, bodyPart: part, recordType: type, mets, isRunning: !!running, ownerId: null });
  }
  const addCustom = (ownerId, name, part, type) => {
    const e = { id: nextId('exercises'), name, bodyPart: part, recordType: type, mets: defaultMets(type, part), isRunning: false, ownerId };
    db.exercises.push(e);
    return e;
  };
  const bulgarian = addCustom(U.yamada, 'ブルガリアンスクワット', 'legs', 'W');
  addCustom(U.yamada, 'ボックスジャンプ', 'legs', 'R');

  const ex = (name) => exerciseByName(name).id;
  const W = (name, sets, id) => ({ exerciseId: id || ex(name), sets: sets.map(([kg, reps]) => ({ weightKg: kg, reps, durationSec: null, distanceKm: null })) });
  const R = (name, reps) => ({ exerciseId: ex(name), sets: reps.map((r) => ({ weightKg: null, reps: r, durationSec: null, distanceKm: null })) });
  const T = (name, sets) => ({ exerciseId: ex(name), sets: sets.map(([sec, km]) => ({ weightKg: null, reps: null, durationSec: sec, distanceKm: km })) });
  const post = (username, daysAgo, exercises, opts = {}) => {
    const w = {
      id: nextId('workouts'), userId: U[username], performedOn: addDays(today, -daysAgo), memo: opts.memo || '',
      durationMin: opts.durationMin ?? null, visibility: opts.visibility || 'public',
      createdAt: opts.createdAt || daysAgoMs(daysAgo, 19 + Math.floor(rnd() * 3), Math.floor(rnd() * 60)),
      editedAt: opts.edited ? daysAgoMs(Math.max(0, daysAgo - 1), 8) : null, exercises, images: opts.images || [],
    };
    db.workouts.push(w);
    return w;
  };
  const r25 = (v) => Math.round(v * 4) / 4;

  // 10週間分の記録
  const weeks = 10;
  for (let i = weeks * 7 - 1; i >= 0; i--) {
    const date = addDays(today, -i);
    const dow = parseYmd(date).getDay();
    const wk = weeks - 1 - Math.floor(i / 7);
    const skip = i > 2 && rnd() < 0.12;
    // 山田：月 胸・水 脚・金 背中・土 ラン
    if (!skip && dow === 1) {
      const top = r25(82.5 + wk * 1.25);
      post('yamada', i, [
        W('ベンチプレス', [[60, 10], [top, 6], [top, 6], [top - 5, 8]]),
        W('インクラインベンチプレス', [[50, 10], [50, 10], [50, 8]]),
        W('ダンベルフライ', [[14, 12], [14, 12], [14, 12]]),
      ], { durationMin: 65, memo: pick(['胸の日。ベンチ少しずつ伸びてる #ベンチプレス #胸トレ', '今日も胸。インクラインがきつい #胸トレ', 'ベンチ #ベンチプレス 💪']), visibility: wk % 4 === 1 ? 'followers' : 'public', images: wk % 3 === 0 ? [svgImage('BENCH', 210)] : [] });
    }
    if (!skip && dow === 3) {
      const top = r25(85 + wk * 2.5);
      post('yamada', i, [
        W('スクワット', [[60, 10], [top, 5], [top, 5]]),
        W('レッグプレス', [[140, 10], [140, 10], [140, 10]]),
        ...(wk >= 6 ? [W('', [[20, 10], [20, 10]], bulgarian.id)] : []),
        W('カーフレイズ', [[60, 15], [60, 15]]),
      ], { durationMin: 75, memo: pick(['今日は脚の日！ #脚トレ', '脚の日。階段がつらい #脚トレ #スクワット', 'スクワットの深さを意識した #脚トレ']), images: wk % 2 === 0 ? [svgImage('SQUAT', 30), svgImage('LEG DAY', 60)] : [] });
    }
    if (!skip && dow === 5) {
      const top = r25(120 + wk * 2.5);
      post('yamada', i, [
        W('デッドリフト', [[100, 5], [top, 3], [top, 3]]),
        W('ラットプルダウン', [[50, 10], [50, 10], [50, 10]]),
        R('懸垂', [10, 8, 7]),
      ], { durationMin: wk === 7 ? null : 60, memo: pick(['背中の日 #背中トレ', 'デッドリフト、腰に気をつけて #背中トレ', '']), visibility: wk === 4 ? 'private' : 'public' });
    }
    if (!skip && dow === 6) {
      const km = 5 + (wk % 3) * 2.5;
      post('yamada', i, [T('ランニング', [[Math.round(km * (360 - wk * 3)), km]])], {
        memo: pick(['朝ラン #朝ラン', '川沿いを走った #ランニング', 'ゆっくりペースで #朝ラン']), images: wk % 4 === 0 ? [svgImage('RUN', 150), svgImage('RIVER', 190), svgImage('MORNING', 40)] : [],
      });
    }
    // 鈴木：火・木・日 ラン
    if (dow === 2) post('suzuki', i, [T('ランニング', [[Math.round(8 * 330), 8]])], { memo: pick(['ゆっくり8km #ランニング', 'ジョグ #朝ラン']) });
    if (dow === 4 && !skip) post('suzuki', i, [T('ランニング', [[180, 0.4], [180, 0.4], [180, 0.4], [1500, 5]])], { memo: 'インターバル走 #ランニング' });
    if (dow === 0) {
      const km = 12 + (wk % 3) * 2;
      post('suzuki', i, [T('ランニング', [[Math.round(km * 340), km]])], { memo: `日曜はロング ${km}km #ロング走 #ランニング`, images: wk % 3 === 2 ? [svgImage('LONG RUN', 100)] : [] });
    }
    // 佐藤（鍵アカウント）：月・木
    if (dow === 1 || dow === 4) {
      post('sato', i, [
        W('ヒップスラスト', [[60 + wk, 12], [60 + wk, 12], [60 + wk, 10]]),
        W('スクワット', [[40, 10], [40, 10], [40, 10]]),
        T('ヨガ', [[1800, null]]),
      ], { durationMin: 70, memo: pick(['下半身とヨガ #ヨガ', 'ヒップスラスト頑張った #脚トレ']), visibility: dow === 4 ? 'followers' : 'public' });
    }
    // 田中：水・土
    if ((dow === 3 || dow === 6) && wk >= 5) {
      post('tanaka', i, [R('腕立て伏せ', [10 + wk, 10 + wk - 2, 8]), W('スクワット', [[20, 10], [20, 10]]), T('ウォーキング', [[1800, 2.5]])], { durationMin: 50, memo: pick(['初心者メニュー #筋トレ初心者', '腕立て少しずつ回数が増えてきた #筋トレ初心者']) });
    }
    // 小林（鍵アカウント）：火・金
    if ((dow === 2 || dow === 5) && !skip) {
      post('kobayashi', i, [W('デッドリフト', [[180, 3], [200, 1]]), W('ベンチプレス', [[120, 5], [120, 5]])], { durationMin: 90, memo: '高重量の日 #パワーリフティング' });
    }
  }
  // 今日の投稿（ナビの「今週のひとこと」や投稿日時の表示を見るため）
  const todayTop = r25(82.5 + 9 * 1.25 + 1.25);
  const todays = db.workouts.filter((w) => w.userId === U.yamada && w.performedOn === today);
  todays.forEach((w) => deleteWorkout(w.id));
  post('yamada', 0, [
    W('ベンチプレス', [[60, 10], [80, 8], [todayTop, 5]]),
    W('ダンベルフライ', [[14, 12], [14, 12], [14, 12]]),
    W('サイドレイズ', [[8, 15], [8, 15], [8, 12]]),
    R('ディップス', [12, 10, 8]),
    W('トライセプスプッシュダウン', [[25, 12], [25, 12]]),
    R('クランチ', [20, 20]),
    T('ランニング', [[600, 2]]),
  ], { durationMin: 80, memo: 'ベンチの自己ベスト更新！\n今日は上半身まとめて。 #ベンチプレス #胸トレ', createdAt: Date.now() - 3 * 60000, images: [svgImage('NEW PR', 0), svgImage('BENCH', 210), svgImage('GYM', 260), svgImage('DIPS', 300)] });
  post('ito', 1, [W('チェストプレス', [[20, 10], [20, 10]]), T('エアロバイク', [[1200, null]])], { durationMin: 40, memo: 'ジム2回目！ #筋トレ初心者', createdAt: Date.now() - 5 * 3600000 });
  post('ito', 3, [T('ウォーキング', [[2400, 3.2]])], { memo: 'まずは歩くところから' });
  // 編集済みの投稿
  const edited = db.workouts.filter((w) => w.userId === U.yamada).sort((a, b) => b.createdAt - a.createdAt)[3];
  if (edited) edited.editedAt = edited.createdAt + 3600 * 1000;

  // フォロー
  const F = (a, b, status = 'accepted', daysAgo = 60) => db.follows.push({ followerId: U[a], followeeId: U[b], status, createdAt: daysAgoMs(daysAgo, 12) });
  F('yamada', 'sato'); F('yamada', 'suzuki'); F('yamada', 'tanaka', 'accepted', 30); F('yamada', 'kobayashi', 'pending', 2);
  F('sato', 'yamada'); F('suzuki', 'yamada'); F('suzuki', 'sato'); F('tanaka', 'yamada', 'accepted', 30); F('tanaka', 'suzuki', 'accepted', 30);
  F('tanaka', 'sato', 'pending', 1); F('ito', 'yamada', 'accepted', 2); F('ito', 'sato', 'pending', 0); F('ito', 'kobayashi', 'pending', 1);
  F('kobayashi', 'yamada'); F('kobayashi', 'suzuki');
  for (const f of db.follows) {
    db.notifications.push({ id: nextId('notifications'), recipientId: f.followeeId, actorId: f.followerId, type: f.status === 'pending' ? 'follow_request' : 'follow', createdAt: f.createdAt, readAt: f.status === 'pending' ? null : f.createdAt });
  }

  // ナイストレーニング・コメント（通知も同じ操作で作る）
  const names = Object.keys(U);
  for (const w of db.workouts) {
    for (const n of names) {
      const uid = U[n];
      if (uid === w.userId || !canViewWorkout(w, uid) || rnd() > 0.45) continue;
      toggleLike(w.id, uid, Math.min(Date.now() - 60000, w.createdAt + Math.floor(rnd() * 3 * 3600000)));
    }
  }
  const yamadaPosts = db.workouts.filter((w) => w.userId === U.yamada && w.visibility === 'public').sort((a, b) => b.createdAt - a.createdAt);
  const latestLeg = yamadaPosts.find((w) => w.exercises.some((e) => e.exerciseId === ex('スクワット')));
  if (latestLeg) {
    const t = latestLeg.createdAt;
    const c1 = addComment(latestLeg.id, U.sato, 'フォームきれいでした！', null, t + 3600000);
    addComment(latestLeg.id, U.yamada, '@sato ありがとうございます！', c1.id, t + 3700000);
    addComment(latestLeg.id, U.suzuki, 'ナイスです', null, t + 4000000);
  }
  const busy = yamadaPosts[6];
  if (busy) {
    const t = busy.createdAt;
    const talk = ['すごい！', 'ナイストレーニング！', '重量伸びてますね', '見習います', 'フォームの動画見たいです', 'お疲れさまでした', '私も頑張ります', '💪💪💪'];
    const commenters = ['suzuki', 'tanaka', 'ito', 'sato', 'kobayashi'];
    for (let k = 0; k < 23; k++) addComment(busy.id, U[commenters[k % commenters.length]], `${talk[k % talk.length]}（${k + 1}）`, null, t + (k + 1) * 600000);
    const root = db.comments.find((c) => c.workoutId === busy.id && c.parentId == null);
    const replies = [['yamada', 'ありがとうございます！'], ['suzuki', '@yamada 次は100kgですね'], ['yamada', '@suzuki がんばります'], ['tanaka', 'すごすぎます'], ['ito', '参考になります'], ['yamada', 'みなさんありがとう！']];
    replies.forEach(([u, text], k) => addComment(busy.id, U[u], text, root.id, root.createdAt + (k + 1) * 60000));
  }

  // 体重（山田・鈴木）
  for (let i = 120; i >= 0; i--) {
    if (i % 2 === 1 && i > 6) continue;
    const kg = Math.round((75.4 - ((120 - i) / 120) * 2.4 + (rnd() - 0.5) * 0.6) * 10) / 10;
    const fat = i % 4 === 0 ? Math.round((20.2 - ((120 - i) / 120) * 1.8 + (rnd() - 0.5) * 0.4) * 10) / 10 : null;
    db.bodyRecords.push({ id: nextId('bodyRecords'), userId: U.yamada, recordedOn: addDays(today, -i), weightKg: kg, bodyFatPct: fat });
    if (i % 7 === 0) db.bodyRecords.push({ id: nextId('bodyRecords'), userId: U.suzuki, recordedOn: addDays(today, -i), weightKg: 62.0, bodyFatPct: 12.0 });
  }

  // 目標
  const G = (username, f) => {
    const g = { id: nextId('goals'), userId: U[username], goalType: f.goalType, name: f.name, period: f.period || null, metric: f.metric || null,
      targetValue: f.targetValue, exerciseId: f.exerciseId || null, bodyPart: f.bodyPart || null, startedOn: addDays(today, -(f.startedDaysAgo ?? 56)),
      deadline: f.deadline || null, achievedOn: null, visibility: f.visibility || 'private', createdAt: daysAgoMs(f.startedDaysAgo ?? 56, 9) };
    db.goals.push(g);
    return g;
  };
  const g1 = G('yamada', { goalType: 'frequency', name: '週4日トレーニング', period: 'week', targetValue: 4, visibility: 'public' });
  G('yamada', { goalType: 'volume', name: '月間ランニング 40km', period: 'month', metric: 'distance', targetValue: 40, visibility: 'followers' });
  G('yamada', { goalType: 'weight', name: 'ベンチプレス 100kg', exerciseId: ex('ベンチプレス'), targetValue: 100, deadline: yearEnd(today) });
  G('yamada', { goalType: 'body_weight', name: '体重 70kg', targetValue: 70, startedDaysAgo: 90 });
  const squat = G('yamada', { goalType: 'weight', name: 'スクワット 100kg', exerciseId: ex('スクワット'), targetValue: 100, startedDaysAgo: 70 });
  G('yamada', { goalType: 'weight', name: 'デッドリフト 200kg', exerciseId: ex('デッドリフト'), targetValue: 200, deadline: addDays(today, -10), startedDaysAgo: 80 });
  const s100 = G('suzuki', { goalType: 'volume', name: '月間ランニング 100km', period: 'month', metric: 'distance', targetValue: 100, visibility: 'public' });
  G('suzuki', { goalType: 'frequency', name: '週3日ラン', period: 'week', targetValue: 3, visibility: 'public' });
  G('sato', { goalType: 'weight', name: 'ヒップスラスト 80kg', exerciseId: ex('ヒップスラスト'), targetValue: 80, visibility: 'followers' });

  // 達成済みの記録を、実際に届いた日で入れる
  const firstSquat100 = db.workouts.filter((w) => w.userId === U.yamada && w.exercises.some((e) => e.exerciseId === ex('スクワット') && e.sets.some((s) => s.weightKg >= 100 && s.reps >= 1)))
    .sort((a, b) => (a.performedOn < b.performedOn ? -1 : 1))[0];
  if (firstSquat100) {
    squat.achievedOn = firstSquat100.performedOn;
    db.notifications.push({ id: nextId('notifications'), recipientId: U.yamada, actorId: null, type: 'goal_achieved', goalId: squat.id, createdAt: firstSquat100.createdAt + 60000, readAt: null });
  }
  for (const g of db.goals.filter((x) => x.period)) {
    for (let p = periodStartOf(g, g.startedOn); p < periodStartOf(g, today); p = g.period === 'week' ? addDays(p, 7) : addMonths(p, 1)) {
      if (goalProgress(g, p).raw >= 1) db.goalAchievements.push({ goalId: g.id, periodStart: p, achievedOn: periodEndOf(g, p) });
    }
  }
  for (const [u, g] of [['suzuki', g1], ['sato', g1], ['tanaka', g1], ['yamada', s100], ['tanaka', s100]]) {
    db.goalCheers.push({ goalId: g.id, userId: U[u], createdAt: daysAgoMs(3, 12) });
    db.notifications.push({ id: nextId('notifications'), recipientId: g.userId, actorId: U[u], type: 'goal_cheer', goalId: g.id, createdAt: daysAgoMs(3, 12 + db.goalCheers.length), readAt: null });
  }

  // テンプレート
  const tpl = (name, exercises, daysAgo) => db.templates.push({ id: nextId('templates'), userId: U.yamada, name, exercises, updatedAt: daysAgoMs(daysAgo, 9) });
  tpl('胸の日', [W('ベンチプレス', [[60, 10], [80, 8], [80, 8]]), W('ダンベルフライ', [[14, 12], [14, 12], [14, 12]])], 5);
  tpl('脚の日', [W('スクワット', [[60, 10], [100, 5], [100, 5]]), W('レッグプレス', [[140, 10], [140, 10]]), W('カーフレイズ', [[60, 15], [60, 15]])], 9);

  // 古い通知は既読にしておく
  for (const n of db.notifications) if (n.createdAt < Date.now() - 2 * 86400000) n.readAt = n.readAt || n.createdAt;
}
