'use strict';
/* FIT LOG モックアップ：S-20 ダッシュボード、S-21 ランニング、S-22 種目ごとの推移、S-23・S-24 目標、S-25 体重 */

const fmtKm = (km) => fmtDist(km);
const qs = (obj) => '?' + new URLSearchParams(Object.entries(obj).filter(([, v]) => v !== '' && v != null)).toString();

/* ===================== S-20 記録（ダッシュボード） ===================== */
function screenDashboard(root, params, q, path) {
  const me = currentUser();
  const today = todayStr();
  const isMonth = path === '/records/month';
  const from = isMonth ? monthStart((q.get('month') || today.slice(0, 7)) + '-01') : weekStart(q.get('week') || today);
  const to = isMonth ? monthEnd(from) : addDays(from, 6);
  const prevFrom = isMonth ? addMonths(from, -1) : addDays(from, -7);
  const prevTo = isMonth ? monthEnd(prevFrom) : addDays(prevFrom, 6);
  const nextFrom = isMonth ? addMonths(from, 1) : addDays(from, 7);
  const metric = q.get('metric') === 'duration' ? 'duration' : 'volume';
  const cal = q.get('cal') || today.slice(0, 7);
  const base = isMonth ? '#/records/month' : '#/records';
  const keep = { metric: q.get('metric'), cal: q.get('cal'), day: q.get('day') };
  const link = (over) => base + qs({ ...(isMonth ? { month: from.slice(0, 7) } : { week: from }), ...keep, ...over });
  const cur = periodStats(me.id, from, to);
  const prev = periodStats(me.id, prevFrom, prevTo);
  const hasAny = db.workouts.some((w) => w.userId === me.id);

  let chart;
  if (!isMonth) {
    const data = dailyValues(me.id, from, to, metric).map((d) => ({ label: fmtDate(d.date), short: dowOf(d.date), value: d.value }));
    chart = barChart(data, { format: (v) => (metric === 'volume' ? `${fmtWeightNum(v)}${weightUnit()}` : `${Math.round(v)}分`) });
  } else {
    const weeks = [];
    for (let s = weekStart(from); s <= to; s = addDays(s, 7)) {
      const st = periodStats(me.id, s < from ? from : s, addDays(s, 6) > to ? to : addDays(s, 6));
      weeks.push({ label: `${fmtMD(s < from ? from : s)}〜`, value: metric === 'volume' ? st.volume : st.durationSec / 60 });
    }
    chart = barChart(weeks, { format: (v) => (metric === 'volume' ? `${fmtWeightNum(v)}${weightUnit()}` : `${Math.round(v)}分`) });
  }
  const goals = activeGoals(me.id).slice(0, 3);
  const kcalDiff = cur.kcal != null && prev.kcal != null ? diffText(cur.kcal, prev.kcal, fmtInt) : '';
  root.innerHTML = `
    <div class="page-head"><h1>記録</h1><div class="page-head-right"><a href="#/records/running">ランニング ›</a> <a href="#/records/exercises">種目 ›</a></div></div>
    <div class="tabs"><a class="${!isMonth ? 'active' : ''}" href="#/records">週間</a><a class="${isMonth ? 'active' : ''}" href="#/records/month">月間</a></div>
    ${periodSwitch({
      label: isMonth ? `${from.slice(0, 4)}年${Number(from.slice(5, 7))}月` : `${fmtDate(from)} 〜 ${fmtDate(to).slice(-8)}`,
      prev: link(isMonth ? { month: prevFrom.slice(0, 7) } : { week: prevFrom }),
      next: nextFrom <= today ? link(isMonth ? { month: nextFrom.slice(0, 7) } : { week: nextFrom }) : null,
      now: (isMonth ? today.slice(0, 7) !== from.slice(0, 7) : weekStart(today) !== from) ? base : null, nowLabel: isMonth ? '今月' : '今週',
    })}
    ${!hasAny ? '<div class="empty"><p>記録をつけると、ここに週・月の集計が表示されます</p><a class="btn btn-primary" href="#/workouts/new">＋ 記録する</a></div>' : `
    <section class="card stats">
      <div class="stat"><span class="stat-label">トレーニング日数</span><strong>${cur.trainingDays}日</strong>${diffText(cur.trainingDays, prev.trainingDays, (v) => v)}</div>
      <div class="stat"><span class="stat-label">総挙上量</span><strong>${fmtWeightNum(cur.volume)}${weightUnit()}</strong>${diffText(cur.volume, prev.volume, (v) => fmtWeightNum(v))}</div>
      <div class="stat"><span class="stat-label">運動時間</span><strong>${Math.round(cur.durationSec / 60)}分</strong>${diffText(cur.durationSec / 60, prev.durationSec / 60, (v) => Math.round(v))}</div>
      <div class="stat"><span class="stat-label">距離</span><strong>${fmtKm(cur.distance)}</strong>${diffText(distValue(cur.distance), distValue(prev.distance), (v) => v.toFixed(1))}</div>
      <div class="stat wide"><span class="stat-label">消費カロリー 🔒</span><strong>${cur.kcal != null ? `約 ${fmtInt(cur.kcal)}kcal` : '—'}</strong>${kcalDiff}
        ${cur.excluded ? '<span class="sub">※ 体重の記録がない日を除く</span>' : ''}</div>
    </section>
    <section class="card"><div class="row between"><h2 class="section-title">${isMonth ? '週ごと' : '曜日ごと'}</h2>
      <div class="seg"><a class="${metric === 'volume' ? 'active' : ''}" href="${link({ metric: '' })}">総挙上量</a><a class="${metric === 'duration' ? 'active' : ''}" href="${link({ metric: 'duration' })}">運動時間</a></div></div>
      ${chart}</section>
    <section class="card"><h2 class="section-title">部位別のセット数</h2>
      ${Object.keys(cur.parts).length ? hbars(BODY_PARTS.filter(([p]) => cur.parts[p]).map(([p, l]) => ({ label: l, value: cur.parts[p] })).sort((a, b) => b.value - a.value)) : '<p class="empty">この期間の記録はありません</p>'}</section>`}
    <section class="card"><div class="row between"><h2 class="section-title">目標</h2><a href="#/goals">すべて見る ›</a></div>
      ${goals.length ? goals.map((g) => { const p = goalProgress(g); const pct = ratePercent(p.raw); return `<a class="goal-line" href="#/goals"><span>${esc(g.name)}</span>${progressBar(pct)}<span class="sub">${p.label}</span><span>${pct}%</span></a>`; }).join('') : '<p class="empty small">目標はまだありません <a href="#/goals/new">＋ 目標を作る</a></p>'}</section>
    <section class="card" id="calendar">${calendarHtml(me, cal, q.get('day'), (c, d) => link({ cal: c, day: d }))}</section>
    <div id="day-list" class="feed"></div>`;
  const day = q.get('day');
  if (day) {
    const ws = db.workouts.filter((w) => w.userId === me.id && w.performedOn === day).sort((a, b) => a.createdAt - b.createdAt);
    $('#day-list', root).innerHTML = `<h2 class="section-title">${fmtDate(day)} の記録</h2>${ws.length ? ws.map((w) => workoutCard(w, me)).join('') : '<p class="empty">この日の記録はありません</p>'}`;
    if (q.get('scroll')) $('#day-list', root).scrollIntoView();
  }
}
function calendarHtml(me, ym, selected, link) {
  const first = ym + '-01';
  const last = monthEnd(first);
  const today = todayStr();
  const days = new Set(db.workouts.filter((w) => w.userId === me.id && w.performedOn >= first && w.performedOn <= last).map((w) => w.performedOn));
  const lead = (parseYmd(first).getDay() + 6) % 7;
  const cells = [];
  for (let i = 0; i < lead; i++) cells.push('<span></span>');
  for (let d = first; d <= last; d = addDays(d, 1)) {
    const n = Number(d.slice(8));
    cells.push(`<a class="cal-day ${days.has(d) ? 'has' : ''} ${d === selected ? 'sel' : ''} ${d === today ? 'today' : ''}" href="${link(ym, d)}&scroll=1" aria-label="${fmtDate(d)}${days.has(d) ? ' 記録あり' : ''}">${n}${days.has(d) ? '<i>●</i>' : ''}</a>`);
  }
  const prev = addMonths(first, -1).slice(0, 7), next = addMonths(first, 1).slice(0, 7);
  return `<div class="row between"><h2 class="section-title">カレンダー</h2>
    <div class="period"><a class="btn btn-sm" href="${link(prev, '')}" aria-label="前の月">‹</a><span>${ym.slice(0, 4)}年${Number(ym.slice(5))}月</span>
    ${next <= today.slice(0, 7) ? `<a class="btn btn-sm" href="${link(next, '')}" aria-label="次の月">›</a>` : '<span class="btn btn-sm disabled">›</span>'}</div></div>
    <div class="cal"><b>月</b><b>火</b><b>水</b><b>木</b><b>金</b><b>土</b><b>日</b>${cells.join('')}</div>`;
}

/* ===================== S-21 ランニング ===================== */
function screenRunning(root, params, q) {
  const me = currentUser();
  const today = todayStr();
  const period = ['week', 'month', 'year'].includes(q.get('period')) ? q.get('period') : 'month';
  const date = q.get('date') || today;
  const range = (d) => (period === 'week' ? [weekStart(d), addDays(weekStart(d), 6)] : period === 'month' ? [monthStart(d), monthEnd(d)] : [yearStart(d), yearEnd(d)]);
  const [from, to] = range(date);
  const prevD = period === 'week' ? addDays(from, -7) : period === 'month' ? addMonths(from, -1) : `${Number(from.slice(0, 4)) - 1}-01-01`;
  const nextD = period === 'week' ? addDays(from, 7) : period === 'month' ? addMonths(from, 1) : `${Number(from.slice(0, 4)) + 1}-01-01`;
  const cur = runningStats(me.id, from, to);
  const prev = runningStats(me.id, ...range(prevD));
  const label = period === 'week' ? `${fmtDate(from)} 〜 ${fmtDate(to).slice(-8)}` : period === 'month' ? `${from.slice(0, 4)}年${Number(from.slice(5, 7))}月` : `${from.slice(0, 4)}年`;
  const link = (p, d) => `#/records/running${qs({ period: p, date: d })}`;
  const best = (r, icon, name, main) => (r ? `<a class="best" href="#/workouts/${r.workout.id}">${icon} ${name}　<strong>${main}</strong><span class="sub">（${fmtDist(r.distanceKm)}・${fmtDuration(r.durationSec)}・${fmtMD(r.workout.performedOn)}）</span> ›</a>` : `<p class="sub">${icon} ${name}　—</p>`);
  let yearChart = '';
  if (period === 'year') {
    const data = [];
    for (let m = 0; m < 12; m++) { const s = addMonths(from, m); data.push({ label: `${m + 1}月`, short: String(m + 1), value: distValue(runningStats(me.id, s, monthEnd(s)).distance) }); }
    yearChart = `<section class="card"><h2 class="section-title">月ごとの走行距離</h2>${barChart(data, { format: (v) => `${v.toFixed(1)}${distanceUnit() === 'mi' ? 'mi' : 'km'}` })}</section>`;
  }
  root.innerHTML = page('ランニング', `
    <div class="tabs">${[['week', '週間'], ['month', '月間'], ['year', '年間']].map(([p, l]) => `<a class="${p === period ? 'active' : ''}" href="${link(p, date)}">${l}</a>`).join('')}</div>
    ${periodSwitch({ label, prev: link(period, prevD), next: nextD <= today ? link(period, nextD) : null, now: range(today)[0] !== from ? link(period, '') : null, nowLabel: { week: '今週', month: '今月', year: '今年' }[period] })}
    ${!cur.count ? '<p class="empty">この期間のランニングの記録はありません</p>' : `
    <section class="card stats">
      <div class="stat"><span class="stat-label">走行距離</span><strong>${fmtDist(cur.distance)}</strong>${diffText(distValue(cur.distance), distValue(prev.distance), (v) => v.toFixed(2))}</div>
      <div class="stat"><span class="stat-label">走行時間</span><strong>${fmtDuration(cur.durationSec)}</strong>${diffText(cur.durationSec, prev.durationSec, fmtDuration)}</div>
      <div class="stat"><span class="stat-label">回数</span><strong>${cur.count}回</strong>${diffText(cur.count, prev.count, (v) => v)}</div>
      <div class="stat"><span class="stat-label">平均ペース</span><strong>${fmtPace(cur.avgPace)}</strong>${cur.avgPace && prev.avgPace ? diffText(cur.avgPace, prev.avgPace, (v) => fmtDuration(distanceUnit() === 'mi' ? v * KM_PER_MI : v), { lowerIsBetter: true }) : ''}</div>
    </section>
    <section class="card"><h2 class="section-title">ベスト記録</h2>
      ${best(cur.fastest, '⚡', '最速ペース', cur.fastest ? fmtPace(cur.fastest.durationSec / cur.fastest.distanceKm) : '')}<p class="sub small">（1km 以上の記録から）</p>
      ${best(cur.longest, '📏', '最長距離', cur.longest ? fmtDist(cur.longest.distanceKm) : '')}</section>`}
    ${yearChart}`, { backTo: '#/records' });
}

/* ===================== S-22 種目ごとの推移 ===================== */
function screenTrend(root, params, q) {
  const me = currentUser();
  const used = new Map();
  for (const w of db.workouts.filter((x) => x.userId === me.id)) for (const x of w.exercises) if (!used.has(x.exerciseId) || used.get(x.exerciseId) < w.performedOn) used.set(x.exerciseId, w.performedOn);
  if (!params.id) {
    const list = [...used.keys()].map(exerciseById);
    root.innerHTML = page('種目ごとの推移', BODY_PARTS.map(([p, l]) => {
      const es = list.filter((e) => e.bodyPart === p);
      return es.length ? `<h2 class="section-title">${l}</h2><div class="list">${es.map((e) => `<a class="pick-row" href="#/records/exercises/${e.id}">${esc(e.name)}<span class="sub">最後の記録 ${fmtDate(used.get(e.id))}</span></a>`).join('')}</div>` : '';
    }).join('') || '<p class="empty">まだ記録した種目がありません</p>', { backTo: '#/records' });
    return;
  }
  const e = exerciseById(Number(params.id));
  if (!e || (e.ownerId && e.ownerId !== me.id)) { root.innerHTML = page('種目ごとの推移', '<p class="empty">この種目は見つかりません</p>', { backTo: '#/records/exercises' }); return; }
  const range = ['3m', '1y', 'all'].includes(q.get('range')) ? q.get('range') : '3m';
  const from = range === '3m' ? addDays(todayStr(), -91) : range === '1y' ? addDays(todayStr(), -365) : null;
  const pts = exerciseTrend(me.id, e.id, from);
  const allPts = exerciseTrend(me.id, e.id, null);
  const view = q.get('view') || (e.recordType === 'W' ? 'max' : e.recordType === 'R' ? 'reps' : 'dist');
  const link = (o) => `#/records/exercises/${e.id}${qs({ range, view, ...o })}`;
  let header = '', toggle = '', series = [], rows = '';
  if (e.recordType === 'W') {
    const pb = personalBestOf(me.id, e.id);
    const bestE = allPts.filter((p) => p.maxE1rmKg).sort((a, b) => b.maxE1rmKg - a.maxE1rmKg)[0];
    header = `${pb ? `<p>🏆 自己ベスト <strong>${fmtWeight(pb.weightKg)}</strong>（${fmtDate(pb.performedOn)}）</p>` : ''}${bestE ? `<p>推定 1RM の最大 <strong>${fmtWeight(bestE.maxE1rmKg)}</strong>（${fmtDate(bestE.date)}）</p>` : ''}
      <p class="sub small">推定 1RM は推定値です（1〜10回のセットから Epley 式で計算）</p>`;
    toggle = `<div class="seg"><a class="${view === 'max' ? 'active' : ''}" href="${link({ view: 'max' })}">最大重量</a><a class="${view === 'e1rm' ? 'active' : ''}" href="${link({ view: 'e1rm' })}">推定 1RM</a></div>`;
    series = [{ name: view === 'e1rm' ? '推定 1RM' : '最大重量', color: 'var(--accent)', points: pts.filter((p) => (view === 'e1rm' ? p.maxE1rmKg : p.maxWeightKg)).map((p) => ({ x: p.date, y: weightValue(view === 'e1rm' ? p.maxE1rmKg : p.maxWeightKg) })) }];
    rows = pts.slice().reverse().map((p) => `<a class="pick-row" href="#/workouts/${p.workoutId}">${fmtMD(p.date)}　${p.maxWeightKg ? `最大 ${fmtWeight(p.maxWeightKg)} × ${p.maxWeightReps}回` : '—'}　${p.maxE1rmKg ? `推定 1RM ${fmtWeight(p.maxE1rmKg)}` : ''} ›</a>`).join('');
  } else if (e.recordType === 'R') {
    toggle = `<div class="seg"><a class="${view === 'reps' ? 'active' : ''}" href="${link({ view: 'reps' })}">合計回数</a><a class="${view === 'max' ? 'active' : ''}" href="${link({ view: 'max' })}">最大回数</a></div>`;
    series = [{ name: view === 'max' ? '最大回数' : '合計回数', color: 'var(--accent)', points: pts.map((p) => ({ x: p.date, y: view === 'max' ? p.maxReps : p.totalReps })) }];
    rows = pts.slice().reverse().map((p) => `<a class="pick-row" href="#/workouts/${p.workoutId}">${fmtMD(p.date)}　合計 ${p.totalReps}回　最大 ${p.maxReps}回 ›</a>`).join('');
  } else {
    toggle = `<div class="seg"><a class="${view === 'dist' ? 'active' : ''}" href="${link({ view: 'dist' })}">合計距離</a><a class="${view === 'time' ? 'active' : ''}" href="${link({ view: 'time' })}">合計時間</a>${e.isRunning ? `<a class="${view === 'pace' ? 'active' : ''}" href="${link({ view: 'pace' })}">ペース</a>` : ''}</div>`;
    series = [{ name: { dist: '合計距離', time: '合計時間（分）', pace: 'ペース（秒）' }[view], color: 'var(--accent)',
      points: pts.filter((p) => (view === 'dist' ? p.distanceKm : view === 'pace' ? p.pace : true)).map((p) => ({ x: p.date, y: view === 'dist' ? distValue(p.distanceKm) : view === 'pace' ? p.pace : p.durationSec / 60 })) }];
    rows = pts.slice().reverse().map((p) => `<a class="pick-row" href="#/workouts/${p.workoutId}">${fmtMD(p.date)}　${fmtDuration(p.durationSec)}　${p.distanceKm ? fmtDist(p.distanceKm) : ''}　${p.pace ? fmtPace(p.pace) : ''} ›</a>`).join('');
  }
  const fmtY = view === 'pace' ? (v) => fmtPace(v) : view === 'time' ? (v) => `${Math.round(v)}分` : e.recordType === 'W' ? (v) => `${trimNum(v)}${weightUnit()}` : view === 'dist' ? (v) => `${v.toFixed(2)}${distanceUnit() === 'mi' ? 'mi' : 'km'}` : (v) => `${v}回`;
  root.innerHTML = page('種目ごとの推移', `
    <div class="row"><button class="btn" id="pick-ex">種目: ${esc(e.name)} ▾</button></div>
    <div class="tabs">${[['3m', '3か月'], ['1y', '1年'], ['all', '全期間']].map(([r, l]) => `<a class="${r === range ? 'active' : ''}" href="#/records/exercises/${e.id}${qs({ range: r, view })}">${l}</a>`).join('')}</div>
    ${header ? `<section class="card">${header}</section>` : ''}
    <section class="card"><div class="row between"><h2 class="section-title">推移</h2>${toggle}</div>${lineChart(series, { format: fmtY })}</section>
    <section class="card"><h2 class="section-title">記録の一覧（新しい順）</h2><div class="list">${rows || '<p class="empty">この期間の記録はありません</p>'}</div></section>`, { backTo: '#/records/exercises' });
  $('#pick-ex', root).addEventListener('click', () => openExercisePicker({ title: '種目を選ぶ', filter: (x) => used.has(x.id), onSelect: (x) => go(`#/records/exercises/${x.id}?range=${range}`) }));
}

/* ===================== S-23 目標一覧 ===================== */
function goalSortKey(g) {
  if (g.period) return periodEndOf(g, periodStartOf(g, todayStr()));
  return g.deadline || '9999-12-31';
}
function activeGoals(userId) {
  return db.goals.filter((g) => g.userId === userId && !g.achievedOn)
    .sort((a, b) => (isExpired(a) ? 1 : 0) - (isExpired(b) ? 1 : 0) || (goalSortKey(a) < goalSortKey(b) ? -1 : 1));
}
function goalCard(g, me, achievedTab = false) {
  const p = goalProgress(g);
  const pct = ratePercent(p.raw);
  const cheers = db.goalCheers.filter((c) => c.goalId === g.id).length;
  const term = g.period ? (g.period === 'week' ? '毎週' : '毎月') : g.deadline ? `〜${fmtDate(g.deadline)}` : '期限なし';
  const hist = g.period ? goalHistory(g) : null;
  return `<article class="card goal" data-goal="${g.id}">
    <div class="goal-head"><h3>${esc(g.name)}</h3><span class="sub">${term}</span>${isExpired(g) ? '<span class="tag warn">期限切れ</span>' : ''}${g.achievedOn ? `<span class="tag ok">達成 ${fmtDate(g.achievedOn)}</span>` : ''}
      ${visIcon(g.visibility)}${cheers ? `<span class="sub">📣 応援 ${cheers}</span>` : ''}
      <span class="menu-wrap"><button class="icon-btn" data-action="toggle-menu" aria-label="目標のメニュー">…</button><span class="menu menu-right" hidden>
        <a href="#/goals/${g.id}/edit">編集</a><button class="danger" data-gdel="${g.id}">削除</button></span></span></div>
    ${achievedTab && g.period ? '' : `<div class="goal-bar">${g.period ? `<span class="sub">${g.period === 'week' ? '今週' : '今月'}</span>` : '<span></span>'}${progressBar(pct)}<span class="sub">${p.label}</span><strong>${pct}%</strong></div>`}
    ${hist ? `<details class="history" ${achievedTab ? 'open' : ''}><summary>これまで：達成 ${hist.achieved.length}${g.period === 'week' ? '週' : 'か月'} / ${hist.total}${g.period === 'week' ? '週' : 'か月'}　履歴 ›</summary>
      <ul>${hist.achieved.map((a) => `<li>${fmtDate(a.periodStart)}〜 の${g.period === 'week' ? '週' : '月'}　達成（${fmtMD(a.achievedOn)}）</li>`).join('') || '<li class="sub">まだ達成した期間はありません</li>'}</ul></details>` : ''}
  </article>`;
}
function screenGoals(root, params, q) {
  const me = currentUser();
  const tab = q.get('tab') === 'achieved' ? 'achieved' : 'active';
  const active = activeGoals(me.id);
  const full = db.goals.filter((g) => g.userId === me.id && !g.achievedOn).length >= 10;
  const achieved = [
    ...db.goals.filter((g) => g.userId === me.id && g.achievedOn).sort((a, b) => (a.achievedOn < b.achievedOn ? 1 : -1)),
    ...db.goals.filter((g) => g.userId === me.id && g.period && db.goalAchievements.some((a) => a.goalId === g.id)),
  ];
  root.innerHTML = `<div class="page-head"><h1>目標</h1><div class="page-head-right">${full ? '<button class="btn btn-sm" disabled>＋ 目標を作る</button>' : '<a class="btn btn-primary btn-sm" href="#/goals/new">＋ 目標を作る</a>'}</div></div>
    ${full ? '<p class="notice">進行中の目標は10件までです</p>' : ''}
    <div class="tabs"><a class="${tab === 'active' ? 'active' : ''}" href="#/goals">進行中</a><a class="${tab === 'achieved' ? 'active' : ''}" href="#/goals?tab=achieved">達成済み</a></div>
    <div class="list">${tab === 'active'
      ? (active.length ? active.map((g) => goalCard(g, me)).join('') : '<div class="empty"><p>目標を立てて、続ける力にしましょう</p><a class="btn btn-primary" href="#/goals/new">＋ 目標を作る</a></div>')
      : (achieved.length ? achieved.map((g) => goalCard(g, me, true)).join('') : '<p class="empty">達成した目標はまだありません</p>')}</div>`;
  root.addEventListener('click', async (e) => {
    const id = Number(e.target.dataset.gdel);
    if (!id) return;
    if (await confirmDialog({ title: '目標を削除しますか？', message: '応援も消えます', ok: '削除する', danger: true })) {
      db.goals = db.goals.filter((g) => g.id !== id);
      db.goalCheers = db.goalCheers.filter((c) => c.goalId !== id);
      db.goalAchievements = db.goalAchievements.filter((a) => a.goalId !== id);
      removeNotifications((n) => n.goalId === id);
      toast('目標を削除しました'); rerender();
    }
  });
}

/* ===================== S-24 目標の作成・編集 ===================== */
function goalInputValue(g) {
  if (g.goalType === 'weight') return trimNum(weightValue(g.targetValue));
  if (g.goalType === 'body_weight') return trimNum(bodyValue(g.targetValue), 1);
  if (g.goalType === 'volume' && g.metric === 'volume') return String(Math.round(weightUnit() === 'lb' ? g.targetValue / KG_PER_LB : g.targetValue));
  if (g.goalType === 'volume' && g.metric === 'distance') return trimNum(distValue(g.targetValue));
  return String(g.targetValue);
}
/** 目標値のチェック（機能要件の目標値の範囲）。入力した単位で確かめる */
function goalTargetError(f) {
  const v = String(f.target).trim();
  if (!v) return '目標値を入力してください';
  const n = Number(v);
  if (!isFinite(n)) return '数字で入力してください';
  const lb = weightUnit() === 'lb', mi = distanceUnit() === 'mi';
  const check = (min, max, step, unit) => (n < min || n > max ? `${min}〜${max.toLocaleString()}${unit}で入力してください` : !isStep(n, step) ? `${step}${unit} 刻みで入力してください` : '');
  if (f.goalType === 'frequency') return !isInt(v) || n < 1 || n > (f.period === 'month' ? 31 : 7) ? `1〜${f.period === 'month' ? 31 : 7}日の整数で入力してください` : '';
  if (f.goalType === 'weight') return lb ? check(0.5, 1100, 0.5, 'lb') : check(0.25, 500, 0.25, 'kg');
  if (f.goalType === 'body_weight') return lb ? check(44, 661, 0.2, 'lb') : check(20, 300, 0.1, 'kg');
  if (f.metric === 'volume') return !isInt(v) || n < 1 || n > 999999 ? `1〜999,999${lb ? 'lb' : 'kg'}の整数で入力してください` : '';
  if (f.metric === 'duration') return !isInt(v) || n < 1 || n > 44640 ? '1〜44,640分の整数で入力してください' : '';
  return mi ? check(0.01, 9631.31, 0.01, 'マイル') : check(0.01, 15500, 0.01, 'km');
}
function screenGoalEdit(root, params) {
  const me = currentUser();
  const g = params.id ? goalById(Number(params.id)) : null;
  if (params.id && (!g || g.userId !== me.id)) { root.innerHTML = page('目標', '<p class="empty">この目標は見つかりません</p>', { backTo: '#/goals' }); return; }
  if (!g && db.goals.filter((x) => x.userId === me.id && !x.achievedOn).length >= 10) { go('#/goals'); return; }
  const f = g
    ? { goalType: g.goalType, period: g.period || 'week', metric: g.metric || 'distance', target: goalInputValue(g), filter: g.exerciseId ? 'exercise' : g.bodyPart ? 'part' : 'none', exerciseId: g.exerciseId, bodyPart: g.bodyPart || 'legs', deadline: g.deadline || '', name: g.name, visibility: g.visibility, nameEdited: true }
    : { goalType: 'frequency', period: 'week', metric: 'distance', target: '4', filter: 'none', exerciseId: null, bodyPart: 'legs', deadline: '', name: '', visibility: 'private', nameEdited: false };
  const render = () => {
    if (!f.nameEdited) f.name = autoGoalName({ ...f, targetValue: f.target, exerciseId: f.goalType === 'volume' && f.filter !== 'exercise' ? null : f.exerciseId, bodyPart: f.goalType === 'volume' && f.filter === 'part' ? f.bodyPart : null });
    const startRec = f.goalType === 'body_weight' ? (g ? bodyGoalWeights(g)?.start : bodyRecordOnOrBefore(me.id, todayStr()) || db.bodyRecords.filter((b) => b.userId === me.id)[0]) : null;
    const noBody = f.goalType === 'body_weight' && !db.bodyRecords.some((b) => b.userId === me.id);
    const pb = f.goalType === 'weight' && f.exerciseId ? personalBestOf(me.id, f.exerciseId) : null;
    const unit = { frequency: '日', weight: weightUnit(), body_weight: weightUnit(), volume: f.metric === 'volume' ? weightUnit() : f.metric === 'duration' ? '分' : distanceUnit() === 'mi' ? 'マイル' : 'km' }[f.goalType];
    const radio = (name, v, label, cur, dis = false) => `<label class="radio"><input type="radio" name="${name}" value="${v}" ${cur === v ? 'checked' : ''} ${dis ? 'disabled' : ''}> ${label}</label>`;
    const err = goalTargetError(f);
    const exErr = (f.goalType === 'weight' || (f.goalType === 'volume' && f.filter === 'exercise')) && !f.exerciseId ? '種目を選んでください' : '';
    const nameN = charCount(f.name);
    const deadlineErr = f.deadline && f.deadline <= todayStr() ? '期限は明日以降の日付にしてください' : '';
    const invalid = !!err || !!exErr || nameN < 1 || nameN > 30 || noBody || !!deadlineErr;
    root.innerHTML = `
      <div class="ed-head"><a class="icon-btn" href="#/goals" aria-label="閉じる">×</a><h1>${g ? '目標の編集' : '目標を作る'}</h1><button class="btn btn-primary" id="gsave" ${invalid ? 'disabled' : ''}>保存する</button></div>
      <form id="gf" class="card" novalidate>
        <fieldset class="field"><legend class="field-label">種類${g ? '（編集では変えられません）' : ''}</legend>
          ${[['frequency', '頻度　週・月に何日トレーニングするか'], ['volume', '量　週・月の合計（距離など）'], ['weight', '重量　種目の重さ'], ['body_weight', '体重']].map(([v, l]) => radio('goalType', v, l, f.goalType, !!g)).join('<br>')}</fieldset>
        ${['frequency', 'volume'].includes(f.goalType) ? `<div class="field"><span class="field-label">期間</span>${radio('period', 'week', '毎週', f.period)}${radio('period', 'month', '毎月', f.period)}</div>` : ''}
        ${f.goalType === 'volume' ? `<div class="field"><span class="field-label">何の合計か</span>${radio('metric', 'volume', '総挙上量', f.metric)}${radio('metric', 'duration', '運動時間', f.metric)}${radio('metric', 'distance', '距離', f.metric)}</div>
          <div class="field"><span class="field-label">絞り込み</span>${radio('filter', 'none', 'なし', f.filter)}${radio('filter', 'exercise', '種目1つ', f.filter)}${radio('filter', 'part', '部位1つ', f.filter)}
            ${f.filter === 'exercise' ? `<button type="button" class="btn btn-sm" id="gex">${f.exerciseId ? esc(exerciseById(f.exerciseId).name) : '種目を選ぶ'} ▾</button>` : ''}
            ${f.filter === 'part' ? `<select name="bodyPart">${BODY_PARTS.map(([p, l]) => `<option value="${p}" ${p === f.bodyPart ? 'selected' : ''}>${l}</option>`).join('')}</select>` : ''}</div>` : ''}
        ${f.goalType === 'weight' ? `<div class="field"><span class="field-label">種目（重量 × 回数の種目）</span><button type="button" class="btn btn-sm" id="gex">${f.exerciseId ? esc(exerciseById(f.exerciseId).name) : '種目を選ぶ'} ▾</button>
          ${f.exerciseId ? `<p class="sub">今の自己ベスト：${pb ? fmtWeight(pb.weightKg) : 'まだ記録がありません'}</p>` : ''}<span class="error">${f.exerciseId ? '' : ''}</span></div>` : ''}
        ${noBody ? '<p class="notice">先に体重を記録してください <a class="btn btn-sm" href="#/body">体重を記録する</a></p>' : `
        <label class="field"><span class="field-label">目標</span><span><input name="target" value="${esc(f.target)}" inputmode="decimal" size="8"> ${unit}</span><span class="error">${err}</span></label>
        ${f.goalType === 'body_weight' && startRec ? `<p class="sub">開始時の体重：${fmtBody(startRec.weightKg)}（${fmtMD(startRec.recordedOn)} の記録）</p>` : ''}`}
        ${['weight', 'body_weight'].includes(f.goalType) ? `<label class="field"><span class="field-label">期限（任意）</span><input type="date" name="deadline" value="${f.deadline}" min="${addDays(todayStr(), 1)}"><span class="error">${deadlineErr}</span></label>` : ''}
        <label class="field"><span class="field-label">名前</span><input name="name" value="${esc(f.name)}">${counter(nameN, 30)}</label>
        ${f.goalType === 'body_weight' ? '<p class="sub">🔒 体重の目標は自分だけが見られます</p>' : `<fieldset class="field"><legend class="field-label">公開範囲</legend>
          ${Object.entries(VIS_ICON).map(([v, [i, l]]) => radio('visibility', v, i + l, f.visibility)).join('')}
          ${f.goalType === 'volume' && f.visibility !== 'private' ? '<p class="sub">達成率から合計がおおよそ分かります</p>' : ''}</fieldset>`}
      </form>`;
    const form = $('#gf', root);
    form.addEventListener('change', (e) => {
      const t = e.target;
      if (['goalType', 'period', 'metric', 'filter', 'visibility', 'bodyPart', 'deadline'].includes(t.name)) {
        f[t.name] = t.value;
        if (t.name === 'goalType') { f.target = { frequency: '4', volume: '', weight: '', body_weight: '' }[t.value]; f.exerciseId = null; if (t.value === 'body_weight') f.visibility = 'private'; }
        render();
      }
    });
    form.addEventListener('input', (e) => {
      const t = e.target;
      if (t.name === 'target') { f.target = t.value; const pos = t.selectionStart; render(); const n = $('[name=target]', root); n.focus(); n.setSelectionRange(pos, pos); }
      if (t.name === 'name') { f.name = t.value; f.nameEdited = true; const pos = t.selectionStart; render(); const n = $('[name=name]', root); n.focus(); n.setSelectionRange(pos, pos); }
    });
    $('#gex', root)?.addEventListener('click', () => openExercisePicker({ title: '種目を選ぶ', filter: (x) => f.goalType !== 'weight' || x.recordType === 'W', onSelect: (x) => { f.exerciseId = x.id; render(); } }));
    $('#gsave', root).addEventListener('click', () => {
      if (invalid) return;
      const n = Number(f.target);
      const target = f.goalType === 'frequency' || f.metric === 'duration' && f.goalType === 'volume' ? n
        : f.goalType === 'volume' && f.metric === 'distance' ? toKm(n) : toKg(n);
      const data = {
        goalType: f.goalType, name: f.name.trim(), targetValue: target,
        period: ['frequency', 'volume'].includes(f.goalType) ? f.period : null, metric: f.goalType === 'volume' ? f.metric : null,
        exerciseId: f.goalType === 'weight' || (f.goalType === 'volume' && f.filter === 'exercise') ? f.exerciseId : null,
        bodyPart: f.goalType === 'volume' && f.filter === 'part' ? f.bodyPart : null,
        deadline: ['weight', 'body_weight'].includes(f.goalType) ? f.deadline || null : null,
        visibility: f.goalType === 'body_weight' ? 'private' : f.visibility,
      };
      if (g) Object.assign(g, data);
      else db.goals.push({ id: nextId('goals'), userId: me.id, startedOn: todayStr(), achievedOn: null, createdAt: Date.now(), ...data });
      const before = db.goals.filter((x) => x.userId === me.id && x.achievedOn).length;
      judgeGoals(me.id); // 作成・編集のあとに達成を判定する（すでに達成している目標値のとき）
      const after = db.goals.filter((x) => x.userId === me.id && x.achievedOn).length;
      refreshNav();
      toast(after > before ? '🎯 目標を達成しました！' : '目標を保存しました');
      go(after > before ? '#/goals?tab=achieved' : '#/goals');
    });
  };
  render();
}

/* ===================== S-25 体重の記録 ===================== */
function screenBody(root, params, q) {
  const me = currentUser();
  const range = q.get('range') === '1y' ? '1y' : '3m';
  const st = { shown: 30, date: todayStr() };
  const unit = weightUnit();
  const recs = () => db.bodyRecords.filter((b) => b.userId === me.id).sort((a, b) => (a.recordedOn < b.recordedOn ? 1 : -1));
  const from = addDays(todayStr(), range === '1y' ? -365 : -91);
  const pts = recs().filter((b) => b.recordedOn >= from).reverse();
  root.innerHTML = page('体重の記録', `
    <form id="bf" class="card" novalidate>
      <div class="row wrap">
        <label class="field inline"><span class="field-label">日付</span><input type="date" name="date" value="${st.date}" max="${todayStr()}"></label>
        <label class="field inline"><span class="field-label">体重</span><span><input name="weight" inputmode="decimal" size="6"> ${unit}</span></label>
        <label class="field inline"><span class="field-label">体脂肪率（任意）</span><span><input name="fat" inputmode="decimal" size="5"> %</span></label>
      </div>
      <p class="error" id="berr"></p>
      <div class="form-foot"><button class="btn btn-primary" id="bsave">記録する</button></div>
    </form>
    <section class="card"><div class="tabs small">${[['3m', '3か月'], ['1y', '1年']].map(([r, l]) => `<a class="${r === range ? 'active' : ''}" href="#/body?range=${r}">${l}</a>`).join('')}</div>
      ${lineChart([
        { name: `体重（${unit}）`, color: 'var(--accent)', points: pts.map((b) => ({ x: b.recordedOn, y: bodyValue(b.weightKg) })) },
        { name: '体脂肪率（%）', color: 'var(--accent-2)', dashed: true, axis: 'right', points: pts.filter((b) => b.bodyFatPct != null).map((b) => ({ x: b.recordedOn, y: b.bodyFatPct })) },
      ], { format: (v) => `${v.toFixed(1)}${unit}`, formatRight: (v) => `${v.toFixed(1)}%` })}
      <p class="sub small">体重と体脂肪率は、それぞれ別の目盛りで描いています</p></section>
    <section class="card"><h2 class="section-title">記録の一覧</h2><div id="blist" class="list"></div></section>`, { backTo: '#/', right: '<span class="sub">🔒 自分だけが見られます</span>' });
  const form = $('#bf', root);
  const fill = () => {
    const r = recs().find((b) => b.recordedOn === form.date.value);
    form.weight.value = r ? trimNum(bodyValue(r.weightKg), 1) : '';
    form.fat.value = r?.bodyFatPct ?? '';
    $('#bsave', root).textContent = r ? '上書きする' : '記録する';
  };
  const renderList = () => {
    const all = recs();
    $('#blist', root).innerHTML = all.slice(0, st.shown).map((b) => `<div class="body-row"><span>${fmtDate(b.recordedOn)}</span><span>${fmtBody(b.weightKg)}</span><span>${b.bodyFatPct != null ? b.bodyFatPct.toFixed(1) + '%' : '—'}</span>
      <button class="btn btn-sm" data-bdel="${b.id}">削除</button></div>`).join('') + (all.length > st.shown ? '<button class="btn btn-block" id="bmore">さらに表示</button>' : '') || '<p class="empty">まだ記録がありません</p>';
  };
  form.date.addEventListener('change', fill);
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const w = Number(form.weight.value), fat = form.fat.value.trim();
    const [min, max, step] = unit === 'lb' ? [44, 661, 0.2] : [20, 300, 0.1];
    let err = '';
    if (!form.date.value || form.date.value > todayStr()) err = '未来の日付は選べません';
    else if (!form.weight.value.trim() || !isFinite(w) || w < min || w > max) err = `体重は${min}〜${max}${unit}で入力してください`;
    else if (!isStep(w, step)) err = `体重は${step}${unit} 刻みで入力してください`;
    else if (fat && (!isFinite(Number(fat)) || Number(fat) < 1 || Number(fat) > 75 || !isStep(Number(fat), 0.1))) err = '体脂肪率は1.0〜75.0%（0.1 刻み）で入力してください';
    $('#berr', root).textContent = err;
    if (err) return;
    const existing = recs().find((b) => b.recordedOn === form.date.value);
    const data = { weightKg: toKg(w), bodyFatPct: fat ? Number(fat) : null };
    if (existing) Object.assign(existing, data);
    else db.bodyRecords.push({ id: nextId('bodyRecords'), userId: me.id, recordedOn: form.date.value, ...data });
    const before = db.goals.filter((x) => x.userId === me.id && x.achievedOn).length;
    judgeGoals(me.id); // 体重の記録の保存のあとに、体重の目標の達成を判定する
    const achieved = db.goals.filter((x) => x.userId === me.id && x.achievedOn).length > before;
    toast(achieved ? '🎯 目標を達成しました！' : existing ? '体重を上書きしました' : '体重を記録しました');
    rerender();
  });
  root.addEventListener('click', async (e) => {
    if (e.target.id === 'bmore') { st.shown += 30; renderList(); return; }
    const id = Number(e.target.dataset.bdel);
    if (!id) return;
    const b = db.bodyRecords.find((x) => x.id === id);
    const usedAsStart = db.goals.some((g) => g.userId === me.id && g.goalType === 'body_weight' && bodyGoalWeights(g)?.start.id === id);
    if (await confirmDialog({ title: `${fmtDate(b.recordedOn)} の記録を削除しますか？`, message: usedAsStart ? '体重の目標の開始時の体重が変わります' : '', ok: '削除する', danger: true })) {
      db.bodyRecords = db.bodyRecords.filter((x) => x.id !== id); toast('記録を削除しました'); rerender();
    }
  });
  fill();
  renderList();
}
