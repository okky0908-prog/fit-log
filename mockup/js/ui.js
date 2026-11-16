'use strict';
/* FIT LOG モックアップ：共通部品（docs/screens.md の「共通部品」） */

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
function el(html) { const t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstElementChild; }

/* ===================== お知らせ・ダイアログ ===================== */
function toast(msg, ms = 3000) {
  const box = $('#toast');
  box.textContent = msg;
  box.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { box.hidden = true; }, ms);
}
/** 確認ダイアログ。Esc・キャンセルで閉じる。OK なら true */
function confirmDialog({ title, message = '', ok = 'OK', danger = false }) {
  return openDialog(`
    <h2 class="dialog-title">${esc(title)}</h2>
    ${message ? `<p class="dialog-msg">${message}</p>` : ''}
    <div class="dialog-actions">
      <button type="button" class="btn" data-close="cancel">キャンセル</button>
      <button type="button" class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-close="ok">${esc(ok)}</button>
    </div>`).then((r) => r === 'ok');
}
/** 入力欄つきのダイアログ。validate が文字列を返したらエラーとして出す */
function formDialog({ title, message = '', fields, ok = '保存する', validate }) {
  return new Promise((resolve) => {
    const body = `
      <form class="dialog-form" novalidate>
        <h2 class="dialog-title">${esc(title)}</h2>
        ${message ? `<p class="dialog-msg">${message}</p>` : ''}
        ${fields.map((f) => `
          <label class="field"><span class="field-label">${esc(f.label)}</span>
            ${f.type === 'select'
              ? `<select name="${f.name}">${f.options.map(([v, l]) => `<option value="${esc(v)}" ${v === f.value ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`
              : `<input name="${f.name}" type="${f.type || 'text'}" value="${esc(f.value || '')}" ${f.attrs || ''}>`}
          </label>`).join('')}
        <p class="error" data-form-error hidden></p>
        <div class="dialog-actions">
          <button type="button" class="btn" data-close="cancel">キャンセル</button>
          <button type="submit" class="btn btn-primary">${esc(ok)}</button>
        </div>
      </form>`;
    openDialog(body, (dlg, close) => {
      const form = $('form', dlg);
      form.addEventListener('submit', (e) => {
        e.preventDefault();
        const values = Object.fromEntries(new FormData(form).entries());
        const err = validate ? validate(values) : null;
        if (err) { const p = $('[data-form-error]', form); p.textContent = err; p.hidden = false; return; }
        close(values);
      });
      $('input,select', form)?.focus();
    }).then((r) => resolve(r === 'cancel' || r == null ? null : r));
  });
}
/** 汎用のダイアログ。setup(dlg, close) で中身を動かす */
function openDialog(html, setup, cls = '') {
  return new Promise((resolve) => {
    const back = el(`<div class="dialog-back"><div class="dialog ${cls}" role="dialog" aria-modal="true">${html}</div></div>`);
    const dlg = $('.dialog', back);
    const prevFocus = document.activeElement;
    const close = (v) => { back.remove(); document.removeEventListener('keydown', onKey); prevFocus?.focus?.(); resolve(v); };
    const onKey = (e) => { if (e.key === 'Escape') close('cancel'); };
    back.addEventListener('click', (e) => {
      const c = e.target.closest('[data-close]');
      if (c) close(c.dataset.close);
      else if (e.target === back) close('cancel');
    });
    document.addEventListener('keydown', onKey);
    document.body.appendChild(back);
    if (setup) setup(dlg, close);
    else $('[data-close="ok"]', dlg)?.focus();
  });
}
/** 画像の拡大表示。Esc・外側で閉じる。複数枚は ‹ › と矢印キー */
function lightbox(images, index) {
  let i = index;
  const back = el(`<div class="lightbox" role="dialog" aria-modal="true" aria-label="画像の拡大表示">
    <button class="lb-close" aria-label="閉じる">×</button>
    ${images.length > 1 ? '<button class="lb-prev" aria-label="前の画像">‹</button><button class="lb-next" aria-label="次の画像">›</button>' : ''}
    <img alt=""><div class="lb-count"></div></div>`);
  const show = () => { $('img', back).src = images[i]; $('.lb-count', back).textContent = images.length > 1 ? `${i + 1} / ${images.length}` : ''; };
  const move = (d) => { i = (i + d + images.length) % images.length; show(); };
  const close = () => { back.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); if (e.key === 'ArrowLeft') move(-1); if (e.key === 'ArrowRight') move(1); };
  back.addEventListener('click', (e) => {
    if (e.target.closest('.lb-prev')) move(-1);
    else if (e.target.closest('.lb-next')) move(1);
    else if (e.target.closest('.lb-close') || e.target === back) close();
  });
  document.addEventListener('keydown', onKey);
  document.body.appendChild(back);
  show();
}

/* ===================== ユーザー ===================== */
const avatar = (u, size = 40) => `<img class="avatar" src="${u.iconUrl}" width="${size}" height="${size}" alt="">`;
/** ユーザーへのリンク（共通）。アイコン・表示名・@ユーザー名のどこを押しても S-40 へ。鍵アカウントには 🔒 */
function userLink(u, { icon = true, size = 40 } = {}) {
  return `<a class="user-link" href="#/users/${esc(u.username)}" data-stop>
    ${icon ? avatar(u, size) : ''}<span class="user-names"><span class="display-name">${esc(u.displayName)}${u.isPrivate ? ' <span title="鍵アカウント">🔒</span>' : ''}</span>
    <span class="username">@${esc(u.username)}</span></span></a>`;
}
/** フォローのボタン（ユーザー一覧の行・プロフィールで共通） */
function followButton(u, me, { large = false } = {}) {
  if (u.id === me.id) return '';
  const st = followStatus(me.id, u.id);
  if (st === 'following') return `<button class="btn btn-following ${large ? '' : 'btn-sm'}" data-action="unfollow" data-user="${u.id}"><span class="on">フォロー中</span><span class="off">フォロー解除</span></button>`;
  if (st === 'requested') return `<button class="btn ${large ? '' : 'btn-sm'}" data-action="cancel-request" data-user="${u.id}">リクエスト中</button>`;
  return `<button class="btn btn-primary ${large ? '' : 'btn-sm'}" data-action="follow" data-user="${u.id}">${u.isPrivate ? 'フォローをリクエスト' : 'フォローする'}</button>`;
}
function userRow(u, me, right = null) {
  return `<div class="user-row">
    <div class="user-row-main">${userLink(u)}${u.bio ? `<p class="user-bio">${esc(u.bio)}</p>` : ''}</div>
    <div class="user-row-actions">${right ?? followButton(u, me)}</div></div>`;
}

/* ===================== 投稿カード ===================== */
const VIS_ICON = { public: ['🌐', '公開'], followers: ['👥', 'フォロワー限定'], private: ['🔒', '自分のみ'] };
const visIcon = (v) => `<span class="vis" title="${VIS_ICON[v][1]}" aria-label="${VIS_ICON[v][1]}">${VIS_ICON[v][0]}</span>`;
/** ひとこと：改行をそのまま。ハッシュタグはリンク（取り出しと同じ決まり） */
function memoHtml(text) {
  const valid = new Set(extractHashtags(text));
  return esc(text).replace(/(^|[^\p{L}\p{N}_ー])([#＃])([\p{L}\p{N}_ー]+)/gu, (m, pre, sharp, tag) => {
    const t = normalize(tag);
    return valid.has(t) && [...tag].length <= 30 ? `${pre}<a class="hashtag" href="#/search?tag=${encodeURIComponent(t)}" data-stop>${sharp}${tag}</a>` : m;
  });
}
function imageGrid(images) {
  if (!images.length) return '';
  return `<div class="images images-${images.length}">${images.map((src, i) =>
    `<button class="img-btn" data-action="lightbox" data-index="${i}" data-stop aria-label="画像 ${i + 1} を拡大"><img src="${src}" alt="記録の画像 ${i + 1}" loading="lazy"></button>`).join('')}</div>`;
}
function workoutMenu(w) {
  return `<div class="menu-wrap" data-stop>
    <button class="icon-btn" data-action="toggle-menu" aria-label="メニュー" aria-haspopup="true">…</button>
    <div class="menu" hidden>
      <a href="#/workouts/${w.id}/edit">編集</a>
      <a href="#/workouts/new?copy=${w.id}">この記録を複製</a>
      <button data-action="save-template" data-id="${w.id}">テンプレートとして保存</button>
      <button class="danger" data-action="delete-workout" data-id="${w.id}">削除</button>
    </div></div>`;
}
function reactionBar(w, me) {
  const likes = db.likes.filter((l) => l.workoutId === w.id).length;
  const liked = db.likes.some((l) => l.workoutId === w.id && l.userId === me.id);
  const comments = db.comments.filter((c) => c.workoutId === w.id).length;
  return `<div class="reactions" data-stop>
    <a class="reaction" href="#/workouts/${w.id}?focus=comment" aria-label="コメント ${comments}件">💬 <span>${comments || ''}</span></a>
    <button class="reaction like ${liked ? 'liked' : ''}" data-action="like" data-id="${w.id}" aria-pressed="${liked}" aria-label="ナイストレーニング ${likes}件">💪 <span>${likes || ''}</span></button>
  </div>`;
}
function workoutCard(w, me) {
  const u = userById(w.userId);
  const pb = personalBests(w);
  const shown = w.exercises.slice(0, 5);
  const posted = postedLabel(w.createdAt);
  return `<article class="card workout-card" data-href="#/workouts/${w.id}" tabindex="0">
    <header class="card-head">
      ${userLink(u)}
      <div class="card-meta"><span class="date">${fmtDate(w.performedOn)}</span>${visIcon(w.visibility)}${w.userId === me.id ? workoutMenu(w) : ''}</div>
    </header>
    ${posted || w.editedAt ? `<p class="sub">${[posted, w.editedAt ? '編集済み' : ''].filter(Boolean).join(' · ')}</p>` : ''}
    ${w.memo ? `<p class="memo">${memoHtml(w.memo)}</p>` : ''}
    <ul class="summary">${shown.map((ex, i) => `<li>${esc(exerciseSummary(ex))}${pb.exercises.has(i) ? ' <span class="pb">🏆自己ベスト更新</span>' : ''}</li>`).join('')}
      ${w.exercises.length > 5 ? `<li class="more">ほか ${w.exercises.length - 5} 種目</li>` : ''}</ul>
    ${imageGrid(w.images)}
    ${reactionBar(w, me)}
  </article>`;
}

/* ===================== 一覧の続きの読み込み（下までスクロールで次の N 件） ===================== */
function mountInfinite(container, items, render, { pageSize = 20, empty = '', end = 'これ以上の投稿はありません' } = {}) {
  container.innerHTML = '';
  if (!items.length) { container.innerHTML = empty; return; }
  let shown = 0;
  const sentinel = el('<div class="sentinel" aria-hidden="true"></div>');
  const more = () => {
    const next = items.slice(shown, shown + pageSize);
    sentinel.insertAdjacentHTML('beforebegin', next.map(render).join(''));
    shown += next.length;
    if (shown >= items.length) { io.disconnect(); sentinel.replaceWith(el(`<p class="list-end">${end}</p>`)); }
  };
  const io = new IntersectionObserver((es) => { if (es.some((e) => e.isIntersecting)) more(); }, { rootMargin: '200px' });
  container.appendChild(sentinel);
  more();
  if (shown < items.length) io.observe(sentinel);
}

/* ===================== 期間・バー・グラフ ===================== */
function periodSwitch({ label, prev, next, now, nowLabel }) {
  return `<div class="period">
    <a class="btn btn-sm" href="${prev}" aria-label="前の期間">‹</a>
    <span class="period-label">${label}</span>
    ${next ? `<a class="btn btn-sm" href="${next}" aria-label="次の期間">›</a>` : '<span class="btn btn-sm disabled" aria-disabled="true">›</span>'}
    ${now ? `<a class="btn btn-sm" href="${now}">${nowLabel}</a>` : ''}
  </div>`;
}
const progressBar = (pct) => `<div class="bar" role="progressbar" aria-valuenow="${pct}" aria-valuemin="0" aria-valuemax="100"><div style="width:${pct}%"></div></div>`;
/** 差の表示：増えたら＋、減ったら−（色だけでなく記号で） */
function diffText(cur, prev, fmt, { lowerIsBetter = false } = {}) {
  if (prev == null || cur == null) return '';
  const d = cur - prev;
  if (Math.abs(d) < 1e-9) return '<span class="diff">(±0)</span>';
  const good = lowerIsBetter ? d < 0 : d > 0;
  return `<span class="diff ${good ? 'up' : 'down'}">(${d > 0 ? '+' : '−'}${fmt(Math.abs(d))})</span>`;
}
/** 棒グラフ（SVG）。値に乗せると <title> で値を出す。「表で見る」で同じ内容の表 */
function barChart(data, { format = (v) => v, height = 160, color = 'var(--accent)' } = {}) {
  if (!data.some((d) => d.value > 0)) return '<p class="empty">この期間の記録はありません</p>';
  const max = Math.max(...data.map((d) => d.value)) || 1;
  const w = 100 / data.length;
  const bars = data.map((d, i) => {
    const bh = (d.value / max) * (height - 30);
    return `<g><title>${esc(d.label)}：${esc(format(d.value))}</title>
      <rect x="${i * w + w * 0.2}%" y="${height - 20 - bh}" width="${w * 0.6}%" height="${Math.max(bh, d.value > 0 ? 2 : 0)}" rx="3" fill="${color}"></rect>
      <text x="${i * w + w / 2}%" y="${height - 5}" text-anchor="middle" class="axis">${esc(d.short ?? d.label)}</text></g>`;
  }).join('');
  return `<svg class="chart" width="100%" height="${height}" role="img" aria-label="棒グラフ">${bars}</svg>${dataTable(data, format)}`;
}
/** 折れ線グラフ（SVG）。series: [{ name, points: [{ x(ラベル), y }], color, dashed, axis: 'left'|'right' }] */
function lineChart(series, { height = 200, format = (v) => v, formatRight } = {}) {
  const all = series.flatMap((s) => s.points);
  if (!all.length) return '<p class="empty">この期間の記録はありません</p>';
  const labels = [...new Set(all.map((p) => p.x))].sort();
  const W = 600, H = height, pl = 8, pr = 8, pt = 12, pb = 24;
  const x = (lab) => pl + (labels.length === 1 ? (W - pl - pr) / 2 : (labels.indexOf(lab) / (labels.length - 1)) * (W - pl - pr));
  const paths = series.map((s) => {
    if (!s.points.length) return '';
    const ys = s.points.map((p) => p.y);
    let min = Math.min(...ys), max = Math.max(...ys);
    if (min === max) { min -= 1; max += 1; }
    const pad = (max - min) * 0.15; min -= pad; max += pad;
    const y = (v) => pt + (1 - (v - min) / (max - min)) * (H - pt - pb);
    const fmt = s.axis === 'right' && formatRight ? formatRight : format;
    const d = s.points.map((p, i) => `${i ? 'L' : 'M'}${x(p.x).toFixed(1)},${y(p.y).toFixed(1)}`).join(' ');
    return `<path d="${d}" fill="none" stroke="${s.color}" stroke-width="2.5" ${s.dashed ? 'stroke-dasharray="6 5"' : ''} vector-effect="non-scaling-stroke"></path>` +
      s.points.map((p) => `<circle cx="${x(p.x).toFixed(1)}" cy="${y(p.y).toFixed(1)}" r="4" fill="${s.color}"><title>${esc(fmtMD(p.x))} ${esc(s.name)}：${esc(fmt(p.y))}</title></circle>`).join('');
  }).join('');
  const ticks = [labels[0], labels[Math.floor(labels.length / 2)], labels[labels.length - 1]].filter((v, i, a) => a.indexOf(v) === i)
    .map((lab) => `<text x="${x(lab)}" y="${H - 6}" text-anchor="middle" class="axis">${fmtMD(lab)}</text>`).join('');
  const legend = series.length > 1 ? `<div class="legend">${series.map((s) => `<span><i style="border-color:${s.color};border-style:${s.dashed ? 'dashed' : 'solid'}"></i>${esc(s.name)}</span>`).join('')}</div>` : '';
  const rows = labels.map((lab) => ({ label: fmtMD(lab), values: series.map((s) => { const p = s.points.find((q) => q.x === lab); return p ? (s.axis === 'right' && formatRight ? formatRight : format)(p.y) : '—'; }) }));
  return `${legend}<svg class="chart" viewBox="0 0 ${W} ${H}" width="100%" height="${H}" role="img" aria-label="推移のグラフ">${ticks}${paths}</svg>
    <details class="as-table"><summary>表で見る</summary><table><thead><tr><th>日付</th>${series.map((s) => `<th>${esc(s.name)}</th>`).join('')}</tr></thead>
    <tbody>${rows.map((r) => `<tr><td>${r.label}</td>${r.values.map((v) => `<td>${esc(v)}</td>`).join('')}</tr>`).join('')}</tbody></table></details>`;
}
function dataTable(data, format) {
  return `<details class="as-table"><summary>表で見る</summary><table><tbody>${data.map((d) => `<tr><th>${esc(d.label)}</th><td>${esc(format(d.value))}</td></tr>`).join('')}</tbody></table></details>`;
}
/** 横棒（部位別のセット数など） */
function hbars(rows) {
  const total = rows.reduce((a, r) => a + r.value, 0) || 1;
  const max = Math.max(...rows.map((r) => r.value), 1);
  return `<ul class="hbars">${rows.map((r) => `<li><span class="hb-label">${esc(r.label)}</span><span class="hb-track"><span style="width:${(r.value / max) * 100}%"></span></span><span class="hb-val">${r.value}（${Math.round((r.value / total) * 100)}%）</span></li>`).join('')}</ul>`;
}
