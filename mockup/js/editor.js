'use strict';
/* FIT LOG モックアップ：S-13 記録の作成・編集、S-14 テンプレート一覧、種目の選択 */

let ed = null; // 入力中の記録
const draftKey = () => `fitlog-draft-${session.meId}`;
const loadDraft = () => { try { return JSON.parse(localStorage.getItem(draftKey())); } catch { return null; } };
const saveDraft = () => {
  if (!ed || ed.mode !== 'new') return;
  try { localStorage.setItem(draftKey(), JSON.stringify({ ...ed, images: [], draft: null })); } catch { /* 保存できなくても入力は続けられる */ }
};
const clearDraft = () => { try { localStorage.removeItem(draftKey()); } catch { /* なし */ } };

/* ===================== 値の変換とチェック ===================== */
function setToInput(e, s) {
  if (e.recordType === 'W') return { w: trimNum(weightValue(s.weightKg)), reps: String(s.reps) };
  if (e.recordType === 'R') return { reps: String(s.reps) };
  return { time: fmtDuration(s.durationSec), dist: s.distanceKm != null ? trimNum(distValue(s.distanceKm)) : '' };
}
const blankSet = (e) => (e.recordType === 'W' ? { w: '', reps: '' } : e.recordType === 'R' ? { reps: '' } : { time: '', dist: '' });
const toInputExercises = (exercises) => exercises.map((x) => ({ exerciseId: x.exerciseId, sets: x.sets.map((s) => setToInput(exerciseById(x.exerciseId), s)) }));
function parseTime(str) {
  const m = String(str).trim().match(/^(?:(\d{1,2}):)?(\d{1,2}):(\d{2})$/);
  if (!m) return null;
  const [h, mi, s] = [Number(m[1] || 0), Number(m[2]), Number(m[3])];
  if (s >= 60 || (m[1] !== undefined && mi >= 60)) return null;
  return h * 3600 + mi * 60 + s;
}
const isStep = (v, step) => Math.abs(v / step - Math.round(v / step)) < 1e-9;
const isInt = (s) => /^\d+$/.test(String(s).trim());
/** セットの1つの欄のエラー（なければ ''） */
function fieldError(e, key, raw) {
  const v = String(raw ?? '').trim();
  const unit = weightUnit();
  if (key === 'w') {
    if (!v) return '重量を入力してください';
    const n = Number(v);
    const [min, max, step] = unit === 'lb' ? [0.5, 1100, 0.5] : [0.25, 500, 0.25];
    if (!isFinite(n) || n < min || n > max) return `${min}〜${max}${unit}で入力してください`;
    if (!isStep(n, step)) return `${step}${unit} 刻みで入力してください`;
  }
  if (key === 'reps') {
    if (!v) return '回数を入力してください';
    const [min, max] = e.recordType === 'W' ? [0, 999] : [1, 9999];
    if (!isInt(v) || Number(v) < min || Number(v) > max) return `${min}〜${max}回の整数で入力してください`;
  }
  if (key === 'time') {
    if (!v) return '時間を入力してください';
    const sec = parseTime(v);
    if (sec == null) return '「時:分:秒」か「分:秒」で入力してください';
    if (sec < 1 || sec > 86400) return '1秒〜24時間で入力してください';
  }
  if (key === 'dist' && v) {
    const n = Number(v);
    if (!isFinite(n) || n < 0.01 || !isStep(n, 0.01)) return '0.01 刻みで入力してください';
    if (toKm(n) > 500) return '500km までで入力してください';
  }
  return '';
}
function editorErrors() {
  const errs = {};
  if (ed.mode.startsWith('tpl')) {
    const n = charCount(ed.name);
    if (n < 1 || n > 30) errs.name = 'テンプレートの名前は1〜30文字で入力してください';
  } else {
    if (!ed.performedOn || ed.performedOn > todayStr()) errs.performedOn = '未来の日付は選べません';
    if (ed.durationMin !== '' && (!isInt(ed.durationMin) || Number(ed.durationMin) < 1 || Number(ed.durationMin) > 1440)) errs.durationMin = '1〜1440分の整数で入力してください';
    if (charCount(ed.memo) > 280) errs.memo = '280文字までです';
  }
  if (ed.exercises.length < 1) errs.exercises = '種目を1つ以上追加してください';
  if (ed.exercises.length > 20) errs.exercises = '種目は20件までです';
  ed.exercises.forEach((x, i) => {
    const e = exerciseById(x.exerciseId);
    if (x.sets.length < 1 || x.sets.length > 20) errs[`ex${i}`] = 'セットは1〜20件です';
    x.sets.forEach((s, j) => Object.keys(s).forEach((k) => { const m = fieldError(e, k, s[k]); if (m) errs[`${i}-${j}-${k}`] = m; }));
  });
  return errs;
}
function toStoredExercises() {
  return ed.exercises.map((x) => {
    const e = exerciseById(x.exerciseId);
    return {
      exerciseId: x.exerciseId,
      sets: x.sets.map((s) => (e.recordType === 'W' ? { weightKg: toKg(Number(s.w)), reps: Number(s.reps), durationSec: null, distanceKm: null }
        : e.recordType === 'R' ? { weightKg: null, reps: Number(s.reps), durationSec: null, distanceKm: null }
          : { weightKg: null, reps: null, durationSec: parseTime(s.time), distanceKm: s.dist.trim() ? toKm(Number(s.dist)) : null })),
    };
  });
}

/* ===================== S-13 ===================== */
function screenEditor(root, params, q, path) {
  const me = currentUser();
  const isTpl = path.startsWith('/templates');
  if (isTpl) {
    const t = params.id ? db.templates.find((x) => x.id === Number(params.id) && x.userId === me.id) : null;
    if (params.id && !t) { root.innerHTML = page('テンプレート', '<p class="empty">このテンプレートは見つかりません</p>', { backTo: '#/templates' }); return; }
    if (!t && db.templates.filter((x) => x.userId === me.id).length >= 20) { go('#/templates'); return; }
    ed = { mode: t ? 'tpl-edit' : 'tpl-new', id: t?.id, name: t?.name || '', exercises: t ? toInputExercises(t.exercises) : [], dirty: false };
  } else if (params.id) {
    const w = workoutById(Number(params.id));
    if (!w || w.userId !== me.id) { root.innerHTML = page('記録の編集', '<p class="empty">この記録は見つかりません</p>', { backTo: '#/' }); return; }
    ed = { mode: 'edit', id: w.id, performedOn: w.performedOn, durationMin: w.durationMin ? String(w.durationMin) : '', memo: w.memo, visibility: w.visibility,
      images: [...w.images], exercises: toInputExercises(w.exercises), dirty: false };
  } else {
    ed = { mode: 'new', performedOn: todayStr(), durationMin: '', memo: '', visibility: 'public', images: [], exercises: [], dirty: false };
    const copy = workoutById(Number(q.get('copy')));
    const tpl = db.templates.find((x) => x.id === Number(q.get('template')) && x.userId === me.id);
    if (copy && copy.userId === me.id) ed.exercises = toInputExercises(copy.exercises);
    else if (tpl) ed.exercises = toInputExercises(tpl.exercises);
    else {
      const d = loadDraft();
      if (d && (d.exercises.length || d.memo)) ed.draft = d;
    }
  }
  renderEditor(root);
}
function renderEditor(root) {
  const me = currentUser();
  const isTpl = ed.mode.startsWith('tpl');
  const title = { new: '新しい記録', edit: '記録の編集', 'tpl-new': '新しいテンプレート', 'tpl-edit': 'テンプレートの編集' }[ed.mode];
  const submitLabel = { new: '投稿する', edit: '保存する', 'tpl-new': 'テンプレートを保存', 'tpl-edit': 'テンプレートを保存' }[ed.mode];
  const wu = weightUnit(), du = distanceUnit() === 'mi' ? 'マイル' : 'km';
  const exHtml = ed.exercises.map((x, i) => {
    const e = exerciseById(x.exerciseId);
    const head = e.recordType === 'W' ? `<span>重量(${wu})</span><span>回数</span>` : e.recordType === 'R' ? '<span>回数</span>' : `<span>時間</span><span>距離(${du})</span>`;
    return `<section class="ed-ex" data-ex="${i}">
      <div class="ed-ex-head"><h3>■ ${esc(e.name)}（${BODY_PART_LABEL[e.bodyPart]}）${e.ownerId ? ' <span class="star">★</span>' : ''}</h3>
        <span><button type="button" class="icon-btn" data-ex-up="${i}" aria-label="上へ" ${i === 0 ? 'disabled' : ''}>↑</button><button type="button" class="icon-btn" data-ex-down="${i}" aria-label="下へ" ${i === ed.exercises.length - 1 ? 'disabled' : ''}>↓</button>
        <span class="menu-wrap"><button type="button" class="icon-btn" data-action="toggle-menu" aria-label="種目のメニュー">…</button><span class="menu menu-right" hidden>
          <button type="button" data-ex-change="${i}">種目を変える</button><button type="button" class="danger" data-ex-del="${i}">削除</button></span></span></span></div>
      <div class="set-grid t-${e.recordType}"><span>#</span>${head}<span></span>
        ${x.sets.map((s, j) => `<span class="set-no menu-wrap"><button type="button" class="link-btn" data-action="toggle-menu" aria-label="${j + 1}セット目のメニュー">${j + 1}</button>
            <span class="menu" hidden><button type="button" data-set-up="${i}-${j}" ${j === 0 ? 'disabled' : ''}>上へ</button><button type="button" data-set-down="${i}-${j}" ${j === x.sets.length - 1 ? 'disabled' : ''}>下へ</button></span></span>
          ${Object.keys(s).map((k) => `<span class="cell"><input data-set="${i}-${j}-${k}" value="${esc(s[k])}" inputmode="${k === 'time' ? 'numeric' : k === 'reps' ? 'numeric' : 'decimal'}" placeholder="${k === 'time' ? '0:30:00' : ''}" aria-label="${j + 1}セット目の${{ w: '重量', reps: '回数', time: '時間', dist: '距離' }[k]}"><span class="error" data-err="${i}-${j}-${k}"></span></span>`).join('')}
          <button type="button" class="icon-btn" data-set-del="${i}-${j}" aria-label="${j + 1}セット目を消す">×</button>`).join('')}
      </div>
      <p class="error" data-err="ex${i}"></p>
      <button type="button" class="btn btn-sm" data-set-add="${i}" ${x.sets.length >= 20 ? 'disabled' : ''}>＋ セットを追加</button>
    </section>`;
  }).join('');
  root.innerHTML = `
    <div class="ed-head"><button class="icon-btn" id="ed-close" aria-label="閉じる">×</button><h1>${title}</h1><button class="btn btn-primary" id="ed-submit">${submitLabel}</button></div>
    ${ed.draft ? `<div class="notice draft">書きかけの記録があります。続きから入力しますか？（画像は選び直してください）
      <button class="btn btn-sm btn-primary" id="draft-use">続ける</button><button class="btn btn-sm" id="draft-drop">破棄する</button></div>` : ''}
    <form id="ed" class="card editor" novalidate>
      ${ed.mode === 'new' ? `<div class="row"><button type="button" class="btn btn-sm" id="from-tpl">📋 テンプレートから</button><button type="button" class="btn btn-sm" id="from-prev">⧉ 前回の記録</button></div>` : ''}
      ${isTpl ? `<label class="field"><span class="field-label">名前</span><input name="name" value="${esc(ed.name)}" maxlength="40"><span class="error" data-err="name"></span></label>` : `
      <div class="row wrap">
        <label class="field inline"><span class="field-label">実施日</span><input type="date" name="performedOn" value="${ed.performedOn}" max="${todayStr()}"><span class="sub" id="dow">${ed.performedOn ? '（' + dowOf(ed.performedOn) + '）' : ''}</span><span class="error" data-err="performedOn"></span></label>
        <label class="field inline"><span class="field-label">時間</span><input name="durationMin" value="${esc(ed.durationMin)}" inputmode="numeric" size="5"> 分（任意）<span class="error" data-err="durationMin"></span></label>
      </div>`}
      <div id="ed-exercises">${exHtml || '<p class="empty small">種目を追加してください</p>'}</div>
      <p class="error" data-err="exercises"></p>
      <button type="button" class="btn btn-block" id="ex-add" ${ed.exercises.length >= 20 ? 'disabled' : ''}>＋ 種目を追加</button>
      ${isTpl ? '' : `
      <label class="field"><span class="field-label">ひとこと</span><textarea name="memo" rows="3" placeholder="今日は脚の日！ #脚トレ">${esc(ed.memo)}</textarea><span id="memo-count">${counter(charCount(ed.memo), 280)}</span></label>
      <div class="field"><span class="field-label">画像（4枚まで）</span>
        <div class="thumbs">${ed.images.map((src, i) => `<span class="thumb"><img src="${src}" alt="選んだ画像 ${i + 1}"><button type="button" class="icon-btn" data-img-del="${i}" aria-label="画像 ${i + 1} を外す">×</button></span>`).join('')}
          ${ed.images.length < 4 ? '<label class="thumb add" aria-label="画像を追加">＋<input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden id="img-in"></label>' : ''}</div>
        <p class="error" id="img-err"></p></div>
      <fieldset class="field"><legend class="field-label">公開範囲</legend>
        ${Object.entries(VIS_ICON).map(([v, [i, l]]) => `<label class="radio"><input type="radio" name="visibility" value="${v}" ${ed.visibility === v ? 'checked' : ''}> ${i}${l}</label>`).join('')}
        ${me.isPrivate ? '<p class="sub">鍵アカウントなので、「公開」でも承認したフォロワーだけに見えます</p>' : ''}</fieldset>`}
    </form>`;
  bindEditor(root);
  validateEditor(root);
}
/** エラーは、入力した欄（ed.touched）と、投稿を押したあと（ed.showAll）だけ出す */
function validateEditor(root) {
  const errs = editorErrors();
  ed.touched ||= {};
  $$('[data-err]', root).forEach((s) => {
    const key = s.dataset.err;
    const show = ed.showAll || ed.touched[key] || /^ex\d+$/.test(key);
    s.textContent = show ? errs[key] || '' : '';
  });
  $('#ed-submit', root).disabled = Object.keys(errs).length > 0;
  return errs;
}
function bindEditor(root) {
  const form = $('#ed', root);
  const changed = () => { ed.dirty = true; saveDraft(); validateEditor(root); };
  form.addEventListener('input', (e) => {
    const t = e.target;
    (ed.touched ||= {})[t.dataset.set || t.name] = true;
    if (t.dataset.set) { const [i, j, k] = t.dataset.set.split('-'); ed.exercises[i].sets[j][k] = t.value; }
    else if (t.name === 'memo') { ed.memo = t.value; $('#memo-count', root).innerHTML = counter(charCount(t.value), 280); }
    else if (t.name === 'performedOn') { ed.performedOn = t.value; $('#dow', root).textContent = t.value ? '（' + dowOf(t.value) + '）' : ''; }
    else if (t.name === 'durationMin' || t.name === 'name') ed[t.name] = t.value;
    changed();
  });
  form.addEventListener('change', (e) => { if (e.target.name === 'visibility') { ed.visibility = e.target.value; changed(); } });
  const restructure = () => { ed.dirty = true; saveDraft(); renderEditor(root); };
  form.addEventListener('click', (e) => {
    const d = e.target.dataset;
    const idx = (s) => s.split('-').map(Number);
    if (d.setAdd) { // 直前のセットと同じ値で足す
      const x = ed.exercises[d.setAdd];
      x.sets.push(x.sets.length ? { ...x.sets[x.sets.length - 1] } : blankSet(exerciseById(x.exerciseId)));
      restructure();
      $$(`[data-ex="${d.setAdd}"] input`, root).slice(-2)[0]?.focus();
    } else if (d.setDel) { const [i, j] = idx(d.setDel); ed.exercises[i].sets.splice(j, 1); restructure(); }
    else if (d.setUp || d.setDown) {
      const [i, j] = idx(d.setUp || d.setDown); const k = d.setUp ? j - 1 : j + 1; const s = ed.exercises[i].sets;
      [s[j], s[k]] = [s[k], s[j]]; restructure();
    } else if (d.exUp || d.exDown) {
      const i = Number(d.exUp || d.exDown); const k = d.exUp ? i - 1 : i + 1;
      [ed.exercises[i], ed.exercises[k]] = [ed.exercises[k], ed.exercises[i]]; restructure();
    } else if (d.exDel) { ed.exercises.splice(Number(d.exDel), 1); restructure(); }
    else if (d.exChange) {
      const x = ed.exercises[d.exChange];
      const type = exerciseById(x.exerciseId).recordType;
      openExercisePicker({ title: '種目を変える（記録方式が同じ種目だけ）', filter: (e2) => e2.recordType === type, onSelect: (e2) => { x.exerciseId = e2.id; restructure(); } });
    } else if (d.imgDel) { ed.images.splice(Number(d.imgDel), 1); restructure(); }
  });
  $('#ex-add', root).addEventListener('click', () => openExercisePicker({
    title: '種目を選ぶ', onSelect: (e) => { ed.exercises.push({ exerciseId: e.id, sets: [blankSet(e)] }); restructure(); $$('#ed-exercises input', root).slice(-2)[0]?.focus(); },
  }));
  $('#img-in', root)?.addEventListener('change', async (e) => {
    const files = [...e.target.files];
    const room = 4 - ed.images.length;
    const errs = [];
    if (files.length > room) errs.push('画像は4枚までです');
    for (const f of files.slice(0, room)) {
      try { ed.images.push(await resizeImage(f, 1600)); } catch (err) { errs.push(err.message); }
    }
    ed.dirty = true;
    renderEditor(root);
    $('#img-err', root).textContent = errs.join(' ');
  });
  $('#from-tpl', root)?.addEventListener('click', async () => {
    const list = db.templates.filter((t) => t.userId === session.meId).sort((a, b) => b.updatedAt - a.updatedAt);
    const id = await pickFromList('テンプレートから', list.map((t) => [t.id, t.name, t.exercises.map((x) => exerciseById(x.exerciseId).name).join('・')]), 'テンプレートはまだありません');
    if (!id) return;
    if (ed.exercises.length && !(await confirmDialog({ title: '入力中の種目とセットを置き換えます', ok: '置き換える' }))) return;
    ed.exercises = toInputExercises(db.templates.find((t) => t.id === id).exercises); restructure();
  });
  $('#from-prev', root)?.addEventListener('click', async () => {
    const list = db.workouts.filter((w) => w.userId === session.meId).sort((a, b) => (a.performedOn === b.performedOn ? b.createdAt - a.createdAt : a.performedOn < b.performedOn ? 1 : -1)).slice(0, 10);
    const id = await pickFromList('前回の記録', list.map((w) => [w.id, fmtDate(w.performedOn), w.exercises.slice(0, 3).map(exerciseSummary).join(' / ')]), '記録はまだありません');
    if (!id) return;
    if (ed.exercises.length && !(await confirmDialog({ title: '入力中の種目とセットを置き換えます', ok: '置き換える' }))) return;
    ed.exercises = toInputExercises(workoutById(id).exercises); restructure();
  });
  $('#draft-use', root)?.addEventListener('click', () => { const d = ed.draft; ed = { ...d, mode: 'new', images: [], draft: null }; renderEditor(root); });
  $('#draft-drop', root)?.addEventListener('click', () => { clearDraft(); ed.draft = null; renderEditor(root); });
  $('#ed-close', root).addEventListener('click', async () => {
    const hasInput = ed.dirty || (ed.mode === 'new' && (ed.exercises.length || ed.memo));
    if (hasInput && !(await confirmDialog({ title: ed.mode.startsWith('tpl') ? '入力中のテンプレートは保存されません。閉じますか？' : '入力中の記録は保存されません。閉じますか？', ok: '閉じる', danger: true }))) return;
    if (ed.mode === 'new') clearDraft();
    back(ed.mode.startsWith('tpl') ? '#/templates' : '#/');
  });
  $('#ed-submit', root).addEventListener('click', () => submitEditor(root));
}
function submitEditor(root) {
  ed.showAll = true;
  if (Object.keys(validateEditor(root)).length) return;
  const me = currentUser();
  if (ed.mode.startsWith('tpl')) {
    const data = { name: ed.name.trim(), exercises: toStoredExercises(), updatedAt: Date.now() };
    if (ed.mode === 'tpl-edit') Object.assign(db.templates.find((t) => t.id === ed.id), data);
    else db.templates.push({ id: nextId('templates'), userId: me.id, ...data });
    toast('テンプレートを保存しました');
    go('#/templates');
    return;
  }
  const data = { performedOn: ed.performedOn, durationMin: ed.durationMin ? Number(ed.durationMin) : null, memo: ed.memo.trim(), visibility: ed.visibility, images: [...ed.images], exercises: toStoredExercises() };
  let w;
  const isEdit = ed.mode === 'edit';
  if (isEdit) { w = workoutById(ed.id); Object.assign(w, data, { editedAt: Date.now() }); }
  else { w = { id: nextId('workouts'), userId: me.id, createdAt: Date.now(), editedAt: null, ...data }; db.workouts.push(w); clearDraft(); }
  setTimeout(() => { judgeGoals(me.id); refreshNav(); }, 0); // 保存の確定後に達成を判定する
  const pb = personalBests(w);
  const names = [...pb.exercises].map((i) => exerciseById(w.exercises[i].exerciseId).name);
  ed = null;
  go(`#/workouts/${w.id}`);
  toast(names.length ? `🏆 ${names.join('・')}の自己ベストを更新しました！` : isEdit ? '記録を保存しました' : '記録を投稿しました', 4000);
}
/** 一覧から1つ選ぶダイアログ */
function pickFromList(title, rows, empty) {
  return openDialog(`<h2 class="dialog-title">${esc(title)}</h2>
    <div class="pick-list">${rows.length ? rows.map(([id, label, sub]) => `<button class="pick-row" data-close="${id}"><strong>${esc(label)}</strong><span class="sub">${esc(sub)}</span></button>`).join('') : `<p class="empty">${esc(empty)}</p>`}</div>
    <div class="dialog-actions"><button class="btn" data-close="cancel">閉じる</button></div>`).then((r) => (r && r !== 'cancel' ? Number(r) : null));
}

/* ===================== 種目の選択（全画面） ===================== */
function openExercisePicker({ title, onSelect, filter = () => true }) {
  const me = currentUser();
  const st = { q: '', part: '' };
  const back = el(`<div class="picker" role="dialog" aria-modal="true" aria-label="${esc(title)}"></div>`);
  const close = () => { back.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  const usable = () => db.exercises.filter((e) => (e.ownerId == null || e.ownerId === me.id) && filter(e));
  const recent = () => {
    const ids = [];
    for (const w of db.workouts.filter((x) => x.userId === me.id).sort((a, b) => b.createdAt - a.createdAt)) {
      for (const x of w.exercises) if (!ids.includes(x.exerciseId)) ids.push(x.exerciseId);
      if (ids.length >= 10) break;
    }
    return ids.slice(0, 10).map(exerciseById).filter((e) => e && filter(e));
  };
  const row = (e) => `<button class="pick-row ex-row" data-pick="${e.id}" ${e.ownerId ? 'data-custom="1"' : ''}>${esc(e.name)}<span class="sub">${BODY_PART_LABEL[e.bodyPart]}・${RECORD_TYPE_LABEL[e.recordType]}</span>${e.ownerId ? '<span class="star">★自分専用</span>' : ''}</button>`;
  const render = () => {
    const k = normalize(st.q);
    const list = usable().filter((e) => (!k || normalize(e.name).includes(k)) && (!st.part || e.bodyPart === st.part));
    const groups = BODY_PARTS.map(([p, l]) => [l, list.filter((e) => e.bodyPart === p).sort((a, b) => (a.ownerId ? 1 : 0) - (b.ownerId ? 1 : 0))]).filter(([, l]) => l.length);
    $('.picker-list', back).innerHTML = `
      ${!k && !st.part && recent().length ? `<h3>最近使った種目</h3>${recent().map(row).join('')}` : ''}
      ${groups.map(([l, es]) => `<h3>${l}</h3>${es.map(row).join('')}`).join('') || '<p class="empty">見つかりませんでした</p>'}`;
  };
  back.innerHTML = `<div class="picker-head"><button class="icon-btn" data-pclose aria-label="閉じる">←</button><h2>${esc(title)}</h2></div>
    <input type="search" class="picker-q" placeholder="🔍 種目を検索" aria-label="種目を検索">
    <div class="chips">${[['', 'すべて'], ...BODY_PARTS].map(([p, l]) => `<button class="chip ${p === '' ? 'active' : ''}" data-part="${p}">${l}</button>`).join('')}</div>
    <div class="picker-list"></div>
    <div class="picker-foot">見つからないとき <button class="btn btn-sm" data-add>＋ 自分専用の種目を追加</button><span class="sub">★の種目は右クリック・長押しで名前の変更・削除</span></div>`;
  document.body.appendChild(back);
  document.addEventListener('keydown', onKey);
  const qIn = $('.picker-q', back);
  qIn.addEventListener('input', () => { st.q = qIn.value; render(); });
  qIn.focus();
  back.addEventListener('click', async (e) => {
    const t = e.target.closest('button');
    if (!t) return;
    if ('pclose' in t.dataset) close();
    else if ('part' in t.dataset) { st.part = t.dataset.part; $$('.chip', back).forEach((c) => c.classList.toggle('active', c === t)); render(); }
    else if (t.dataset.pick) { close(); onSelect(exerciseById(Number(t.dataset.pick))); }
    else if ('add' in t.dataset) {
      const v = await customExerciseDialog({ name: qIn.value.trim() });
      if (v) { close(); onSelect(v); }
    }
  });
  let pressTimer;
  const customMenu = async (id) => {
    const e = exerciseById(id);
    const r = await openDialog(`<h2 class="dialog-title">${esc(e.name)} ★自分専用</h2><div class="stack">
      <button class="btn" data-close="edit">名前・部位を変える</button><button class="btn btn-danger-outline" data-close="delete">削除</button></div>
      <div class="dialog-actions"><button class="btn" data-close="cancel">閉じる</button></div>`);
    if (r === 'edit') { await customExerciseDialog({ exercise: e }); render(); }
    if (r === 'delete') {
      const used = db.workouts.some((w) => w.exercises.some((x) => x.exerciseId === id)) || db.goals.some((g) => g.exerciseId === id);
      if (used) { toast('記録で使っている種目は削除できません'); return; }
      if (await confirmDialog({ title: `「${e.name}」を削除しますか？`, message: 'テンプレートからも外れます', ok: '削除する', danger: true })) {
        db.exercises = db.exercises.filter((x) => x.id !== id);
        db.templates.forEach((t) => { t.exercises = t.exercises.filter((x) => x.exerciseId !== id); });
        toast('種目を削除しました'); render();
      }
    }
  };
  back.addEventListener('contextmenu', (e) => { const t = e.target.closest('[data-custom]'); if (t) { e.preventDefault(); customMenu(Number(t.dataset.pick)); } });
  back.addEventListener('touchstart', (e) => { const t = e.target.closest('[data-custom]'); if (t) pressTimer = setTimeout(() => customMenu(Number(t.dataset.pick)), 600); }, { passive: true });
  back.addEventListener('touchend', () => clearTimeout(pressTimer));
  render();
}
/** 自分専用の種目の追加・変更（F-15・F-16） */
async function customExerciseDialog({ name = '', exercise = null }) {
  const me = currentUser();
  const fields = [{ name: 'name', label: '名前（1〜30文字）', value: exercise ? exercise.name : name },
    { name: 'bodyPart', label: '部位', type: 'select', value: exercise ? exercise.bodyPart : 'legs', options: BODY_PARTS }];
  if (!exercise) fields.push({ name: 'recordType', label: '記録方式（あとから変えられません）', type: 'select', value: 'W', options: Object.entries(RECORD_TYPE_LABEL) });
  const v = await formDialog({
    title: exercise ? '自分専用の種目を変える' : '自分専用の種目を追加', fields, ok: exercise ? '保存する' : '追加する',
    validate: (x) => {
      const n = charCount(x.name);
      if (n < 1 || n > 30) return '名前は1〜30文字で入力してください';
      const dup = db.exercises.some((e) => e.id !== exercise?.id && (e.ownerId == null || e.ownerId === me.id) && normalize(e.name) === normalize(x.name.trim()));
      if (dup) return '同じ名前の種目があります';
      if (!exercise && db.exercises.filter((e) => e.ownerId === me.id).length >= 100) return '自分専用の種目は100件までです';
      return null;
    },
  });
  if (!v) return null;
  if (exercise) { Object.assign(exercise, { name: v.name.trim(), bodyPart: v.bodyPart, mets: defaultMets(exercise.recordType, v.bodyPart) }); toast('種目を変更しました'); return exercise; }
  const e = { id: nextId('exercises'), name: v.name.trim(), bodyPart: v.bodyPart, recordType: v.recordType, mets: defaultMets(v.recordType, v.bodyPart), isRunning: false, ownerId: me.id };
  db.exercises.push(e);
  toast('自分専用の種目を追加しました');
  return e;
}

/* ===================== S-14 テンプレート一覧 ===================== */
function screenTemplates(root) {
  const me = currentUser();
  const list = db.templates.filter((t) => t.userId === me.id).sort((a, b) => b.updatedAt - a.updatedAt);
  const full = list.length >= 20;
  root.innerHTML = page('テンプレート', `
    ${full ? '<p class="notice">テンプレートは20件までです</p>' : ''}
    <div class="list">${list.length ? list.map((t) => `<div class="card tpl-row"><div><h3>${esc(t.name)}</h3>
      <p class="sub">${esc(t.exercises.map((x) => exerciseById(x.exerciseId).name).join('・'))}（${t.exercises.length}種目）</p></div>
      <div class="row"><a class="btn btn-primary btn-sm" href="#/workouts/new?template=${t.id}">使う</a>
      <span class="menu-wrap"><button class="icon-btn" data-action="toggle-menu" aria-label="メニュー">…</button><span class="menu menu-right" hidden>
        <a href="#/templates/${t.id}/edit">編集</a><button class="danger" data-tdel="${t.id}">削除</button></span></span></div></div>`).join('')
      : '<p class="empty">よく行うメニューを保存しておくと、記録するときに呼び出せます</p>'}</div>`,
  { backTo: '#/', right: full ? '<button class="btn btn-sm" disabled>＋ 新しく作る</button>' : '<a class="btn btn-sm btn-primary" href="#/templates/new">＋ 新しく作る</a>' });
  root.addEventListener('click', async (e) => {
    const id = Number(e.target.dataset.tdel);
    if (!id) return;
    if (await confirmDialog({ title: 'テンプレートを削除しますか？', ok: '削除する', danger: true })) {
      db.templates = db.templates.filter((t) => t.id !== id); toast('テンプレートを削除しました'); rerender();
    }
  });
}
