'use strict';
/* FIT LOG モックアップ：画面（認証・タイムライン・検索・投稿詳細・プロフィール・フォロー・通知・設定） */

const ui = { homeTop: 0, newCount: 0, seenUnread: new Set() };
const go = (hash) => { if (location.hash === hash) rerender(); else location.hash = hash; };
const back = (fallback = '#/') => (history.length > 1 ? history.back() : go(fallback));
const counter = (n, max) => `<span class="counter ${n > max ? 'over' : ''}">${n} / ${max}</span>`;
const page = (title, body, { backTo, right = '' } = {}) => `
  <div class="page-head">${backTo ? `<button class="icon-btn" data-action="back" data-fallback="${backTo}" aria-label="戻る">←</button>` : ''}<h1>${title}</h1><div class="page-head-right">${right}</div></div>${body}`;

/* ===================== ナビゲーション ===================== */
function renderNav(me, path) {
  const unread = unreadCount(me.id);
  const badge = unread ? `<span class="badge">${unread >= 100 ? '99+' : unread}</span>` : '';
  const req = pendingRequests(me.id).length;
  const active = (p) => (p === '/' ? ['/', '/all'].includes(path) : path.startsWith(p)) ? 'active' : '';
  const tabs = [['/', '🏠', 'ホーム'], ['/search', '🔍', '検索'], ['/workouts/new', '＋', '記録する'], ['/records', '📊', '記録'], ['/goals', '🎯', '目標']];
  return `
  <header class="nav">
    <a class="logo" href="#/" data-action="home">FIT LOG</a>
    <nav class="nav-links" aria-label="メインメニュー">
      ${tabs.map(([p, , l]) => (p === '/workouts/new' ? '' : `<a class="${active(p)}" href="#${p}" ${p === '/' ? 'data-action="home"' : ''}>${l}</a>`)).join('')}
      <a class="btn btn-primary btn-sm" href="#/workouts/new">＋ 記録する</a>
    </nav>
    <div class="nav-right">
      <a class="bell" href="#/notifications" aria-label="通知 未読${unread}件">🔔${badge}</a>
      <div class="menu-wrap">
        <button class="icon-btn me-btn" data-action="toggle-menu" aria-haspopup="true" aria-label="自分のメニュー">${avatar(me, 32)} ▾</button>
        <div class="menu menu-right" hidden>
          <a href="#/users/${me.username}">プロフィール</a>
          <a href="#/body">体重の記録</a>
          <a href="#/templates">テンプレート</a>
          <a href="#/follow-requests">フォローのリクエスト${req ? ` <span class="badge inline">${req}</span>` : ''}</a>
          <a href="#/settings">設定</a>
          <button data-action="logout">ログアウト</button>
        </div>
      </div>
    </div>
  </header>
  ${!me.emailVerified && sessionStorage.getItem('hideVerify') !== todayStr() ? `
  <div class="verify-banner" role="status">メールアドレスの確認が済んでいません。届いたメールのリンクを開いてください
    <button class="btn btn-sm" data-action="resend-verify">確認メールを再送</button>
    <button class="icon-btn" data-action="hide-verify" aria-label="閉じる">×</button></div>` : ''}
  <nav class="tabbar" aria-label="メインメニュー">
    ${tabs.map(([p, i, l]) => `<a class="${active(p)}" href="#${p}" ${p === '/' ? 'data-action="home"' : ''}><span aria-hidden="true">${i}</span>${l}</a>`).join('')}
  </nav>`;
}

/* ===================== S-01〜S-05 認証 ===================== */
function screenLogin(root, params, q) {
  root.innerHTML = `<div class="auth"><h1 class="logo big">FIT LOG</h1>
    ${q.get('msg') ? `<p class="notice">${esc(q.get('msg'))}</p>` : ''}
    <form id="login" novalidate>
      <label class="field"><span class="field-label">メールアドレス</span><input name="email" type="email" autocomplete="email" required></label>
      <label class="field"><span class="field-label">パスワード</span><input name="password" type="password" autocomplete="current-password" required></label>
      <p class="error" id="login-error" hidden></p>
      <button class="btn btn-primary btn-block" disabled>ログイン</button>
    </form>
    <p><a href="#/password-reset">パスワードを忘れた方</a></p>
    <p>アカウントをお持ちでない方は<br><a href="#/signup">新規登録はこちら</a></p>
    <div class="demo"><p class="sub">モックのデモ用アカウント（パスワードはすべて password123）。押すと入力されます</p>
      ${db.users.map((u) => `<button class="btn btn-sm" data-email="${u.email}">${esc(u.displayName)}${u.isPrivate ? ' 🔒' : ''}${u.emailVerified ? '' : '（未確認）'}</button>`).join('')}</div></div>`;
  const form = $('#login', root);
  const sync = () => { $('button', form).disabled = !(form.email.value.trim() && form.password.value); };
  form.addEventListener('input', sync);
  $$('.demo button', root).forEach((b) => b.addEventListener('click', () => { form.email.value = b.dataset.email; form.password.value = 'password123'; sync(); }));
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const u = db.users.find((x) => x.email === form.email.value.trim().toLowerCase() && x.password === form.password.value);
    if (!u) {
      $('#login-error', root).textContent = 'メールアドレスまたはパスワードが正しくありません';
      $('#login-error', root).hidden = false;
      form.password.value = ''; sync(); return;
    }
    session.meId = u.id;
    go(q.get('next') || '#/');
  });
}

function screenSignup(root) {
  root.innerHTML = `<div class="auth"><h1>新規登録</h1><form id="signup" novalidate>
    ${[['username', 'ユーザー名', 'text', '半角英数字と _ で4〜15文字'], ['displayName', '表示名', 'text', '1〜50文字'], ['email', 'メールアドレス', 'email', ''],
      ['password', 'パスワード', 'password', '8〜72文字'], ['password2', 'パスワード（確認）', 'password', '']].map(([n, l, t, hint]) =>
      `<label class="field"><span class="field-label">${l}</span><input name="${n}" type="${t}">${hint ? `<span class="hint">${hint}</span>` : ''}<span class="error" data-err="${n}"></span></label>`).join('')}
    <button class="btn btn-primary btn-block">登録する</button></form>
    <p>アカウントをお持ちの方は <a href="#/login">ログイン</a></p></div>`;
  const form = $('#signup', root);
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const v = Object.fromEntries(new FormData(form));
    const err = {};
    if (!/^[A-Za-z0-9_]{4,15}$/.test(v.username)) err.username = '半角英数字と _ で4〜15文字にしてください';
    else if (userByName(v.username) || v.username.toLowerCase() === 'search') err.username = 'このユーザー名はすでに使われています';
    const dn = charCount(v.displayName);
    if (dn < 1 || dn > 50) err.displayName = '1〜50文字で入力してください';
    const email = v.email.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) err.email = 'メールアドレスの形式が正しくありません';
    else if (db.users.some((u) => u.email === email)) err.email = 'このメールアドレスはすでに使われています';
    if (v.password.length < 8 || new TextEncoder().encode(v.password).length > 72) err.password = '8〜72文字（72バイトまで）にしてください';
    if (v.password !== v.password2) err.password2 = 'パスワードが一致しません';
    $$('[data-err]', form).forEach((s) => { s.textContent = err[s.dataset.err] || ''; });
    if (Object.keys(err).length) return;
    const u = { id: nextId('users'), username: v.username, displayName: v.displayName.trim(), email, password: v.password, bio: '', isPrivate: false,
      iconUrl: avatarImage(v.displayName.trim()[0], Math.floor(Math.random() * 360)), weightUnit: 'kg', distanceUnit: 'km', emailVerified: false, createdAt: Date.now() };
    db.users.push(u);
    issueVerifyMail(u);
    session.meId = u.id;
    go('#/');
    toast('確認のメールを送りました。メールのリンクを開いて、メールアドレスの確認を済ませてください', 5000);
  });
}

function screenResetRequest(root) {
  root.innerHTML = `<div class="auth"><h1>パスワードのリセット</h1>
    <p>登録したメールアドレスを入力すると、パスワードを再設定するためのリンクをお送りします。</p>
    <form id="reset" novalidate><label class="field"><span class="field-label">メールアドレス</span><input name="email" type="email"></label>
    <button class="btn btn-primary btn-block">メールを送る</button></form><p id="reset-done" class="notice" hidden></p>
    <p><a href="#/login">ログインに戻る</a></p></div>`;
  $('#reset', root).addEventListener('submit', (e) => {
    e.preventDefault();
    const email = e.target.email.value.trim().toLowerCase();
    const u = db.users.find((x) => x.email === email);
    if (u) {
      const token = 'r' + Math.random().toString(36).slice(2, 10);
      resetTokens[token] = { userId: u.id, expiresAt: Date.now() + 30 * 60000, used: false };
      sendMail(email, '【FIT LOG】パスワードの再設定', '下のリンクを開いて、新しいパスワードを設定してください（30分有効・1回だけ使えます）。', `#/password-reset/confirm?token=${token}`);
    }
    $('#reset-done', root).textContent = '入力したメールアドレスが登録されていれば、再設定のリンクを送りました。30分以内に開いてください（モックでは画面上の「📬 モックのメール」から開けます）';
    $('#reset-done', root).hidden = false;
  });
}

function screenResetConfirm(root, params, q) {
  const t = resetTokens[q.get('token')];
  if (!t || t.used || t.expiresAt <= Date.now()) {
    root.innerHTML = `<div class="auth"><h1>新しいパスワードの設定</h1><p class="error">このリンクは使えません。もう一度リセットを申請してください</p><p><a href="#/password-reset">パスワードのリセットへ</a></p></div>`;
    return;
  }
  root.innerHTML = `<div class="auth"><h1>新しいパスワードの設定</h1><form id="rc" novalidate>
    <label class="field"><span class="field-label">新しいパスワード</span><input name="p1" type="password"><span class="hint">8〜72文字</span></label>
    <label class="field"><span class="field-label">新しいパスワード（確認）</span><input name="p2" type="password"></label>
    <p class="error" id="rc-err"></p><button class="btn btn-primary btn-block">パスワードを変更する</button></form></div>`;
  $('#rc', root).addEventListener('submit', (e) => {
    e.preventDefault();
    const { p1, p2 } = e.target;
    const err = p1.value.length < 8 || p1.value.length > 72 ? '8〜72文字にしてください' : p1.value !== p2.value ? 'パスワードが一致しません' : '';
    $('#rc-err', root).textContent = err;
    if (err) return; // 規則に合わないときは使用済みにしない
    userById(t.userId).password = p1.value;
    t.used = true;
    session.meId = null; // すべての端末でログアウト
    go('#/login?msg=' + encodeURIComponent('パスワードを変更しました。新しいパスワードでログインしてください'));
  });
}

function screenVerify(root, params, q) {
  const t = emailTokens[q.get('token')];
  const me = currentUser();
  let html;
  if (!t || t.expiresAt <= Date.now()) {
    html = `<p class="error">このリンクは使えません</p>${me && !me.emailVerified ? '<button class="btn" data-action="resend-verify">確認メールを再送</button>' : ''}`;
  } else {
    const u = userById(t.userId);
    if (t.email !== u.email && db.users.some((x) => x.email === t.email)) {
      html = '<p class="error">このメールアドレスはすでに使われています</p>';
    } else {
      const changed = t.email !== u.email;
      u.email = t.email; u.emailVerified = true;
      delete emailTokens[q.get('token')];
      html = `<p class="notice">${changed ? 'メールアドレスを変更しました' : 'メールアドレスの確認が済みました'}</p>`;
    }
    html += me ? '<a class="btn btn-primary" href="#/">ホームへ</a>' : '<a class="btn btn-primary" href="#/login">ログインへ</a>';
  }
  root.innerHTML = `<div class="auth"><h1>メールアドレスの確認</h1>${html}</div>`;
}

/* ===================== S-10 ホーム ===================== */
function homeItems(me, tab) {
  return db.workouts
    .filter((w) => canViewWorkout(w, me.id) && (tab === 'all' || w.userId === me.id || isAccepted(me.id, w.userId)))
    .sort((a, b) => b.createdAt - a.createdAt);
}
function weekBox(me) {
  const from = weekStart(todayStr());
  const days = periodStats(me.id, from, addDays(from, 6)).trainingDays;
  const g = db.goals.find((x) => x.userId === me.id && x.goalType === 'frequency' && x.period === 'week');
  if (!g) return `<a class="week-box" href="#/records">今週 ${days}日 トレーニング <span class="link">記録を見る ›</span></a>`;
  const pct = ratePercent(days / g.targetValue);
  return `<a class="week-box" href="#/records"><span>今週 ${days}日 / 目標 ${g.targetValue}日</span>${progressBar(pct)}<span>${pct}%</span><span class="link">記録を見る ›</span></a>`;
}
function screenHome(root, params, q, path) {
  const me = currentUser();
  const tab = path === '/all' ? 'all' : 'following';
  const items = homeItems(me, tab);
  ui.homeTab = tab;
  ui.homeTop = items[0]?.createdAt || Date.now();
  ui.newCount = 0;
  root.innerHTML = `
    <div class="tabs" role="tablist"><a role="tab" class="${tab === 'following' ? 'active' : ''}" href="#/" data-action="home" aria-selected="${tab === 'following'}">フォロー中</a>
      <a role="tab" class="${tab === 'all' ? 'active' : ''}" href="#/all" data-action="home" aria-selected="${tab === 'all'}">全体</a></div>
    ${weekBox(me)}
    <div id="new-posts"></div>
    <div id="feed" class="feed"></div>`;
  mountInfinite($('#feed', root), items, (w) => workoutCard(w, me), {
    empty: tab === 'following'
      ? `<div class="empty"><p>まだ記録がありません。最初の記録をつけてみましょう</p><a class="btn btn-primary" href="#/workouts/new">＋ 記録する</a><p><a href="#/search/users">ユーザーを検索して、仲間をフォローしてみましょう</a></p></div>`
      : '<p class="empty">まだ誰も記録していません</p>',
  });
}
/** 新しい投稿のお知らせ（F-24）：件数だけ数え、ボタンを出す。一覧は勝手に増やさない */
function checkNewPosts() {
  const me = currentUser();
  const box = $('#new-posts');
  if (!me || !box || document.hidden) return;
  const n = homeItems(me, ui.homeTab).filter((w) => w.createdAt > ui.homeTop && w.userId !== me.id).length;
  if (n === ui.newCount) return;
  ui.newCount = n;
  box.innerHTML = n ? `<button class="btn btn-primary new-posts" data-action="home">↑ ${n > 99 ? '99+' : n}件の新しい投稿</button>` : '';
}

/* ===================== S-11 投稿の検索・S-44 ユーザー検索 ===================== */
function frequentTags(me) {
  const recent = db.workouts.filter((w) => w.userId === me.id).sort((a, b) => b.createdAt - a.createdAt).slice(0, 50);
  const count = new Map();
  recent.forEach((w, i) => extractHashtags(w.memo).forEach((t) => { const c = count.get(t) || { n: 0, last: i }; c.n++; c.last = Math.min(c.last, i); count.set(t, c); }));
  return [...count.entries()].sort((a, b) => b[1].n - a[1].n || a[1].last - b[1].last).slice(0, 10).map(([t]) => t);
}
function screenSearch(root, params, q, path) {
  const me = currentUser();
  const isUsers = path === '/search/users';
  const state = { q: q.get('q') || '', tag: q.get('tag') || '', exercise: Number(q.get('exercise')) || null };
  root.innerHTML = `
    <div class="tabs" role="tablist"><a role="tab" class="${!isUsers ? 'active' : ''}" href="#/search${state.q ? '?q=' + encodeURIComponent(state.q) : ''}">投稿</a>
      <a role="tab" class="${isUsers ? 'active' : ''}" href="#/search/users${state.q ? '?q=' + encodeURIComponent(state.q) : ''}">ユーザー</a></div>
    <div class="search-box">
      <div class="search-input"><input id="sq" type="search" maxlength="60" placeholder="${isUsers ? '@ユーザー名・表示名' : 'キーワード'}" value="${esc(state.q)}" aria-label="キーワード"><button class="icon-btn" id="sclear" aria-label="クリア">×</button></div>
      ${isUsers ? '' : `<div class="filters"><button class="btn btn-sm" id="sex">種目: ${state.exercise ? esc(exerciseById(state.exercise).name) : 'すべて'} ▾</button>
        ${state.exercise ? '<button class="chip" id="sex-clear">種目を外す ×</button>' : ''}
        ${state.tag ? `<button class="chip" id="stag-clear">#${esc(state.tag)} ×</button>` : ''}</div>`}
      <p class="error" id="serr"></p>
    </div>
    <div id="sres"></div>`;
  const input = $('#sq', root);
  const run = () => {
    const raw = input.value;
    const url = (isUsers ? '#/search/users' : '#/search') + '?' + new URLSearchParams(Object.entries({ q: raw.trim(), tag: state.tag, exercise: state.exercise || '' }).filter(([, v]) => v)).toString();
    history.replaceState(null, '', url.replace(/\?$/, ''));
    if (isUsers) return runUserSearch(me, raw, $('#sres', root), $('#serr', root));
    let kw = raw.trim();
    if (/^[#＃]./.test(kw)) { // # から始まる語はハッシュタグの条件にする
      return go('#/search?' + new URLSearchParams(Object.entries({ tag: normalize(kw.slice(1)), exercise: state.exercise || '' }).filter(([, v]) => v)));
    }
    if (charCount(kw) > 50) { $('#serr', root).textContent = 'キーワードは50文字までです'; return; }
    $('#serr', root).textContent = '';
    const res = $('#sres', root);
    if (!kw && !state.tag && !state.exercise) {
      const tags = frequentTags(me);
      res.innerHTML = `<div class="empty"><p>キーワード・ハッシュタグ・種目で記録を探せます</p>${tags.length ? `<p class="sub">よく使うハッシュタグ</p><div class="chips">${tags.map((t) => `<a class="chip" href="#/search?tag=${encodeURIComponent(t)}">#${esc(t)}</a>`).join('')}</div>` : ''}</div>`;
      return;
    }
    const k = kw.toLowerCase();
    const items = db.workouts.filter((w) => canViewWorkout(w, me.id)
      && (!k || w.memo.toLowerCase().includes(k))
      && (!state.tag || extractHashtags(w.memo).includes(state.tag))
      && (!state.exercise || w.exercises.some((e) => e.exerciseId === state.exercise))).sort((a, b) => b.createdAt - a.createdAt);
    const title = state.tag ? `<p class="result-title">「#${esc(state.tag)}」の投稿</p>` : '';
    res.innerHTML = title + '<div class="feed"></div>';
    mountInfinite($('.feed', res), items, (w) => workoutCard(w, me), { empty: '<p class="empty">条件に合う記録は見つかりませんでした</p>' });
  };
  let timer;
  input.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(run, 300); });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { clearTimeout(timer); run(); } });
  $('#sclear', root).addEventListener('click', () => { input.value = ''; run(); input.focus(); });
  $('#sex', root)?.addEventListener('click', () => openExercisePicker({ title: '種目で絞り込む', onSelect: (e) => { state.exercise = e.id; go(`#/search?${new URLSearchParams({ q: input.value.trim(), tag: state.tag, exercise: e.id })}`); } }));
  $('#sex-clear', root)?.addEventListener('click', () => { state.exercise = null; run(); go(location.hash); });
  $('#stag-clear', root)?.addEventListener('click', () => { state.tag = ''; run(); go(location.hash); });
  run();
}
function runUserSearch(me, raw, res, errBox) {
  const kw = normalize(raw.trim().replace(/^[@＠]/, ''));
  if ([...kw].length > 50) { errBox.textContent = 'キーワードは50文字までです'; return; }
  errBox.textContent = '';
  const users = kw
    ? db.users.filter((u) => u.username.toLowerCase().includes(kw) || normalize(u.displayName).includes(kw))
    : [...db.users].sort((a, b) => b.createdAt - a.createdAt);
  res.innerHTML = `${kw ? '' : '<p class="result-title">最近参加したユーザー</p>'}<div class="list"></div>`;
  mountInfinite($('.list', res), users, (u) => userRow(u, me), { empty: '<p class="empty">ユーザーが見つかりませんでした</p>', end: '' });
}

/* ===================== S-12 投稿詳細 ===================== */
function setLine(e, s, i, mark) {
  let body;
  if (e.recordType === 'W') body = `${fmtWeight(s.weightKg)} × ${s.reps}回`;
  else if (e.recordType === 'R') body = `${s.reps}回`;
  else body = `${s.distanceKm != null ? fmtDist(s.distanceKm) + '  ' : ''}${fmtDuration(s.durationSec)}${s.distanceKm ? '  ' + fmtPace(s.durationSec / s.distanceKm) : ''}`;
  return `<li><span class="set-no">${i + 1}</span>${body}${mark ? ' <span aria-label="自己ベスト">🏆</span>' : ''}</li>`;
}
function caloriesBox(w) {
  const c = calories(w);
  if (c.status !== 'ok') return `<div class="kcal">🔥 体重を記録すると消費カロリーの目安が表示されます <a class="btn btn-sm" href="#/body">体重を記録する</a></div>`;
  return `<div class="kcal">🔥 消費カロリーの目安 <strong>約 ${fmtInt(c.totalKcal)}kcal</strong>
    <span class="sub">（有酸素 ${fmtInt(c.aerobicKcal)}・筋トレ ${fmtInt(c.strengthKcal)}。${fmtMD(c.bodyWeightRecordedOn)} の体重で計算）</span>
    ${c.strengthIncluded ? '' : '<p class="sub">有酸素の分だけの目安です。トレーニング時間を入れると筋トレの分も計算します</p>'}<span class="only-me">🔒 自分だけに表示</span></div>`;
}
function screenDetail(root, params, q) {
  const me = currentUser();
  const w = workoutById(Number(params.id));
  if (!canViewWorkout(w, me.id)) { root.innerHTML = page('記録', '<p class="empty">この記録は見つかりません</p>', { backTo: '#/' }); return; }
  const u = userById(w.userId);
  const pb = personalBests(w);
  const st = { shown: 20, replies: {}, replyTo: null };
  root.innerHTML = page('記録', `
    <article class="card detail" data-workout="${w.id}">
      <header class="card-head">${userLink(u)}<div class="card-meta"><span class="date">${fmtDate(w.performedOn)}</span>${visIcon(w.visibility)}${w.userId === me.id ? workoutMenu(w) : ''}</div></header>
      ${postedLabel(w.createdAt) || w.editedAt ? `<p class="sub">${[postedLabel(w.createdAt), w.editedAt ? '編集済み' : ''].filter(Boolean).join(' · ')}</p>` : ''}
      ${w.memo ? `<p class="memo">${memoHtml(w.memo)}</p>` : ''}
      ${w.durationMin ? `<p class="sub">トレーニング時間 ${w.durationMin}分</p>` : ''}
      <div class="exercises">${w.exercises.map((ex, i) => {
        const e = exerciseById(ex.exerciseId);
        return `<section class="ex"><h3>■ ${esc(e.name)}（${BODY_PART_LABEL[e.bodyPart]}）${pb.exercises.has(i) ? ' <span class="pb">🏆自己ベスト更新</span>' : ''}</h3>
          <ol class="sets">${ex.sets.map((s, j) => setLine(e, s, j, pb.sets.has(`${i}-${j}`))).join('')}</ol></section>`;
      }).join('')}</div>
      ${imageGrid(w.images)}
      ${w.userId === me.id ? caloriesBox(w) : ''}
      ${reactionBar(w, me)}
    </article>
    <section class="card comment-form">
      <label class="field"><span class="field-label">${w.visibility === 'private' ? 'メモを書く' : 'コメント'}</span>
        <textarea id="cbody" rows="2" placeholder="${w.visibility === 'private' ? '自分の記録へのメモ' : 'コメントを入力'}"></textarea></label>
      <div class="form-foot"><span id="ccount">${counter(0, 140)}</span><button class="btn btn-primary btn-sm" id="csend" disabled>${w.visibility === 'private' ? 'メモする' : 'コメントする'}</button></div>
    </section>
    <section id="comments"></section>`, { backTo: '#/' });

  const renderComments = () => {
    const roots = db.comments.filter((c) => c.workoutId === w.id && !c.parentId).sort((a, b) => a.createdAt - b.createdAt);
    const canDelete = (c) => c.userId === me.id || w.userId === me.id;
    const line = (c, isReply) => {
      const cu = userById(c.userId);
      return `<div class="comment ${isReply ? 'reply' : ''}" id="comment-${c.id}">
        <div class="comment-head">${userLink(cu, { size: 32 })}<span class="sub">· ${relTime(c.createdAt)}</span>
          <span class="comment-actions"><button class="link-btn" data-reply="${c.id}">返信</button>${canDelete(c) ? `<button class="link-btn danger" data-del="${c.id}">削除</button>` : ''}</span></div>
        <p class="comment-body">${esc(c.content)}</p></div>`;
    };
    const html = roots.slice(0, st.shown).map((c) => {
      const reps = db.comments.filter((r) => r.parentId === c.id).sort((a, b) => a.createdAt - b.createdAt);
      const n = st.replies[c.id] || 3;
      return `<div class="thread">${line(c, false)}<div class="replies">${reps.slice(0, n).map((r) => line(r, true)).join('')}
        ${reps.length > n ? `<button class="link-btn" data-more-replies="${c.id}">返信をさらに表示（${reps.length - n}件）</button>` : ''}
        ${st.replyTo?.root === c.id ? `<div class="reply-form"><textarea rows="2" id="rbody" aria-label="返信を入力">${esc(st.replyTo.prefix)}</textarea>
          <div class="form-foot"><span id="rcount">${counter(charCount(st.replyTo.prefix), 140)}</span><button class="btn btn-sm" data-cancel-reply>キャンセル</button><button class="btn btn-primary btn-sm" id="rsend">返信する</button></div></div>` : ''}</div></div>`;
    }).join('');
    $('#comments', root).innerHTML = html + (roots.length > st.shown ? '<button class="btn btn-block" data-more-comments>さらに表示</button>' : '');
    const rb = $('#rbody', root);
    if (rb) {
      rb.focus(); rb.setSelectionRange(rb.value.length, rb.value.length);
      const sync = () => { const n = charCount(rb.value); $('#rcount', root).innerHTML = counter(n, 140); $('#rsend', root).disabled = n < 1 || n > 140; };
      rb.addEventListener('input', sync); sync();
      $('#rsend', root).addEventListener('click', () => {
        addComment(w.id, me.id, rb.value.trim(), st.replyTo.root);
        st.replies[st.replyTo.root] = 1e9; st.replyTo = null;
        renderComments(); updateCommentCount();
      });
    }
  };
  const updateCommentCount = () => { $('.reactions', root).outerHTML = reactionBar(w, me); };
  $('#comments', root).addEventListener('click', async (e) => {
    const t = e.target;
    if (t.dataset.reply) {
      const c = db.comments.find((x) => x.id === Number(t.dataset.reply));
      st.replyTo = { root: c.parentId || c.id, prefix: c.parentId ? `@${userById(c.userId).username} ` : '' };
      renderComments();
    } else if (t.dataset.del) {
      const c = db.comments.find((x) => x.id === Number(t.dataset.del));
      if (await confirmDialog({ title: 'コメントを削除しますか？', message: c.parentId ? '' : '返信も削除されます', ok: '削除する', danger: true })) {
        deleteComment(c.id); renderComments(); updateCommentCount(); toast('コメントを削除しました');
      }
    } else if (t.dataset.moreReplies) { st.replies[t.dataset.moreReplies] = (st.replies[t.dataset.moreReplies] || 3) + 20; renderComments(); }
    else if ('moreComments' in t.dataset) { st.shown += 20; renderComments(); }
    else if ('cancelReply' in t.dataset) { st.replyTo = null; renderComments(); }
  });
  const cb = $('#cbody', root);
  cb.addEventListener('input', () => { const n = charCount(cb.value); $('#ccount', root).innerHTML = counter(n, 140); $('#csend', root).disabled = n < 1 || n > 140; });
  $('#csend', root).addEventListener('click', () => {
    addComment(w.id, me.id, cb.value.trim());
    cb.value = ''; cb.dispatchEvent(new Event('input'));
    st.shown = 1e9; renderComments(); updateCommentCount();
  });
  renderComments();
  if (q.get('focus') === 'comment') cb.focus();
}

/* ===================== S-40 プロフィール・S-41 編集・S-42 一覧・S-43 リクエスト ===================== */
function goalMini(g, me) {
  const p = goalProgress(g);
  const pct = ratePercent(p.raw);
  const cheers = db.goalCheers.filter((c) => c.goalId === g.id).length;
  const cheered = db.goalCheers.some((c) => c.goalId === g.id && c.userId === me.id);
  return `<div class="goal-mini"><span class="goal-name">${esc(g.name)}</span>${progressBar(pct)}<span>${pct}%</span>
    ${g.userId === me.id ? `<span class="sub">📣 ${cheers}</span>` : `<button class="btn btn-sm ${cheered ? 'btn-on' : ''}" data-action="cheer" data-id="${g.id}" aria-pressed="${cheered}">📣 ${cheered ? '応援中' : '応援する'} ${cheers || ''}</button>`}</div>`;
}
function screenProfile(root, params) {
  const me = currentUser();
  const u = userByName(params.username);
  if (!u) { root.innerHTML = page('プロフィール', '<p class="empty">このアカウントは存在しません</p>', { backTo: '#/' }); return; }
  const canView = canViewAccount(u, me.id);
  const following = db.follows.filter((f) => f.followerId === u.id && f.status === 'accepted').length;
  const followers = db.follows.filter((f) => f.followeeId === u.id && f.status === 'accepted').length;
  const goals = db.goals.filter((g) => !g.achievedOn && canViewGoal(g, me.id) && g.goalType !== 'body_weight' && (g.userId !== me.id || g.visibility !== 'private'));
  const items = db.workouts.filter((w) => w.userId === u.id && canViewWorkout(w, me.id))
    .sort((a, b) => (a.performedOn === b.performedOn ? b.createdAt - a.createdAt : a.performedOn < b.performedOn ? 1 : -1));
  const countLink = (n, label, to) => (canView ? `<a href="#/users/${u.username}/${to}"><strong>${n}</strong> ${label}</a>` : `<span><strong>${n}</strong> ${label}</span>`);
  root.innerHTML = page(esc(u.displayName), `
    <section class="card profile">
      <div class="profile-top">${avatar(u, 80)}<div>${u.id === me.id ? '<a class="btn" href="#/settings/profile">プロフィールを編集</a>' : followButton(u, me, { large: true })}</div></div>
      <h2>${esc(u.displayName)}${u.isPrivate ? ' 🔒' : ''}</h2><p class="username">@${esc(u.username)}</p>
      ${u.bio ? `<p>${esc(u.bio)}</p>` : ''}
      <p class="counts">${countLink(following, 'フォロー中', 'following')}　${countLink(followers, 'フォロワー', 'followers')}</p>
    </section>
    ${canView ? `
      ${goals.length ? `<section class="card"><h2 class="section-title">公開している目標</h2><div id="pgoals">${goals.slice(0, 3).map((g) => goalMini(g, me)).join('')}</div>
        ${goals.length > 3 ? '<button class="link-btn" id="allgoals">すべて見る</button>' : ''}</section>` : ''}
      <h2 class="section-title">記録</h2><div id="pfeed" class="feed"></div>`
    : '<p class="empty">🔒 このアカウントの記録は、承認されたフォロワーだけが見られます</p>'}`, { backTo: '#/' });
  if (!canView) return;
  $('#allgoals', root)?.addEventListener('click', (e) => { $('#pgoals', root).innerHTML = goals.map((g) => goalMini(g, me)).join(''); e.target.remove(); });
  mountInfinite($('#pfeed', root), items, (w) => workoutCard(w, me), { empty: '<p class="empty">まだ記録がありません</p>' });
}
function screenEditProfile(root) {
  const me = currentUser();
  let icon = me.iconUrl;
  root.innerHTML = page('プロフィール編集', `<form id="pe" class="card" novalidate>
    <div class="icon-edit"><img class="avatar" id="peicon" src="${icon}" width="80" height="80" alt="アイコン"><label class="btn btn-sm">画像を選ぶ<input type="file" accept="image/jpeg,image/png,image/webp" hidden id="pefile"></label></div>
    <p class="error" id="peferr"></p>
    <label class="field"><span class="field-label">表示名</span><input name="displayName" value="${esc(me.displayName)}"><span class="error" data-err="dn"></span></label>
    <label class="field"><span class="field-label">自己紹介</span><textarea name="bio" rows="3">${esc(me.bio)}</textarea><span id="biocount">${counter(charCount(me.bio), 160)}</span></label>
    <div class="form-foot"><a class="btn" href="#/users/${me.username}">キャンセル</a><button class="btn btn-primary">保存する</button></div></form>`, { backTo: `#/users/${me.username}` });
  const form = $('#pe', root);
  form.bio.addEventListener('input', () => { $('#biocount', root).innerHTML = counter(charCount(form.bio.value), 160); });
  $('#pefile', root).addEventListener('change', async (e) => {
    const f = e.target.files[0];
    $('#peferr', root).textContent = '';
    try { icon = await resizeImage(f, 512, true); $('#peicon', root).src = icon; } catch (err) { $('#peferr', root).textContent = err.message; }
  });
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    const dn = charCount(form.displayName.value);
    $('[data-err="dn"]', form).textContent = dn < 1 || dn > 50 ? '1〜50文字で入力してください' : '';
    if (dn < 1 || dn > 50 || charCount(form.bio.value) > 160) return;
    Object.assign(me, { displayName: form.displayName.value.trim(), bio: form.bio.value.trim(), iconUrl: icon });
    toast('プロフィールを保存しました');
    go(`#/users/${me.username}`);
  });
}
/** 画像の縮小（ブラウザで行う）。square なら中央を切り抜く */
function resizeImage(file, maxSide, square = false) {
  return new Promise((resolve, reject) => {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 20 * 1024 * 1024) {
      reject(new Error('この画像は使えません（JPEG・PNG・WebP、20MBまで）')); return;
    }
    const img = new Image();
    img.onload = () => {
      const c = document.createElement('canvas');
      if (square) {
        const s = Math.min(img.width, img.height), out = Math.min(s, maxSide);
        c.width = c.height = out;
        c.getContext('2d').drawImage(img, (img.width - s) / 2, (img.height - s) / 2, s, s, 0, 0, out, out);
      } else {
        const r = Math.min(1, maxSide / Math.max(img.width, img.height));
        c.width = Math.round(img.width * r); c.height = Math.round(img.height * r);
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
      }
      resolve(c.toDataURL('image/jpeg', 0.85));
      URL.revokeObjectURL(img.src);
    };
    img.onerror = () => reject(new Error('この画像は使えません（JPEG・PNG・WebP、20MBまで）'));
    img.src = URL.createObjectURL(file);
  });
}
function screenFollowList(root, params, q, path) {
  const me = currentUser();
  const u = userByName(params.username);
  if (!u) { root.innerHTML = page('フォロー', '<p class="empty">このアカウントは存在しません</p>', { backTo: '#/' }); return; }
  const tab = path.endsWith('/followers') ? 'followers' : 'following';
  const body = !canViewAccount(u, me.id)
    ? '<p class="empty">🔒 このアカウントのフォロー・フォロワーは、承認されたフォロワーだけが見られます</p>'
    : '<div id="flist" class="list"></div>';
  root.innerHTML = page(esc(u.displayName), `<div class="tabs"><a class="${tab === 'following' ? 'active' : ''}" href="#/users/${u.username}/following">フォロー中</a>
    <a class="${tab === 'followers' ? 'active' : ''}" href="#/users/${u.username}/followers">フォロワー</a></div>${body}`, { backTo: `#/users/${u.username}` });
  if (!canViewAccount(u, me.id)) return;
  const rows = db.follows.filter((f) => f.status === 'accepted' && (tab === 'following' ? f.followerId === u.id : f.followeeId === u.id))
    .sort((a, b) => b.createdAt - a.createdAt).map((f) => userById(tab === 'following' ? f.followeeId : f.followerId));
  mountInfinite($('#flist', root), rows, (x) => userRow(x, me), { empty: `<p class="empty">${tab === 'following' ? 'まだ誰もフォローしていません' : 'まだフォロワーはいません'}</p>`, end: '' });
}
function screenRequests(root) {
  const me = currentUser();
  const reqs = pendingRequests(me.id);
  const right = (u) => `<button class="btn btn-primary btn-sm" data-approve="${u.id}">承認</button><button class="btn btn-sm" data-reject="${u.id}">拒否</button>`;
  root.innerHTML = page('フォローのリクエスト', !me.isPrivate && !reqs.length
    ? '<p class="empty">鍵アカウントにすると、フォローを承認制にできます <a href="#/settings">設定へ</a></p>'
    : `<div class="list">${reqs.length ? reqs.map((f) => userRow(userById(f.followerId), me, right(userById(f.followerId)))).join('') : '<p class="empty">フォローのリクエストはありません</p>'}</div>`, { backTo: '#/' });
  root.addEventListener('click', async (e) => {
    const a = e.target.dataset.approve, r = e.target.dataset.reject;
    if (a) { approveRequest(me.id, Number(a)); toast('リクエストを承認しました'); rerender(); }
    if (r && await confirmDialog({ title: 'リクエストを拒否しますか？', message: '拒否したことは相手に通知されません', ok: '拒否する', danger: true })) { rejectRequest(me.id, Number(r)); rerender(); }
  });
}

/* ===================== S-30 通知一覧 ===================== */
function notificationText(n) {
  const a = n.actorId ? userById(n.actorId) : null;
  const name = a ? `<strong>${esc(a.displayName)}</strong>さん` : '';
  const w = n.workoutId ? workoutById(n.workoutId) : null;
  const c = n.commentId ? db.comments.find((x) => x.id === n.commentId) : null;
  const g = n.goalId ? goalById(n.goalId) : null;
  const quote = (s) => (s ? `「${esc([...s].slice(0, 20).join(''))}${[...s].length > 20 ? '…' : ''}」` : '');
  switch (n.type) {
    case 'like': return `${name}があなたの記録にナイストレーニングしました${quote(w?.memo)}`;
    case 'comment': return `${name}があなたの記録にコメントしました${quote(c?.content)}`;
    case 'reply': return `${name}があなたのコメントに返信しました${quote(c?.content)}`;
    case 'follow': return `${name}にフォローされました`;
    case 'follow_request': return `${name}からフォローのリクエストが届きました`;
    case 'follow_accepted': return `${name}がフォローのリクエストを承認しました`;
    case 'goal_achieved': return `🎯 目標「${esc(g?.name)}」を達成しました${n.periodStart ? `（${fmtMD(n.periodStart)}〜の${g?.period === 'week' ? '週' : '月'}）` : ''}`;
    case 'goal_cheer': return `${name}があなたの目標「${esc(g?.name)}」を応援しました`;
    default: return '';
  }
}
function notificationHref(n) {
  if (n.workoutId) return `#/workouts/${n.workoutId}${n.commentId ? '?comment=' + n.commentId : ''}`;
  if (n.type === 'follow_request') return '#/follow-requests';
  if (n.type === 'goal_achieved') return '#/goals?tab=achieved';
  if (n.type === 'goal_cheer') return '#/goals';
  return `#/users/${userById(n.actorId).username}`;
}
function screenNotifications(root) {
  const me = currentUser();
  const list = visibleNotifications(me.id);
  // 開いたときに既読にする。● は画面を離れるまで残す
  const unreadIds = new Set(list.filter((n) => !n.readAt).map((n) => n.id));
  list.forEach((n) => { n.readAt = n.readAt || Date.now(); });
  root.innerHTML = page('通知', '<div id="nlist" class="list"></div>', { backTo: '#/' });
  const render = (n) => {
    const a = n.actorId ? userById(n.actorId) : null;
    const pending = n.type === 'follow_request' && followRow(n.actorId, me.id)?.status === 'pending';
    return `<a class="notification ${unreadIds.has(n.id) ? 'unread' : ''}" href="${notificationHref(n)}">
      ${unreadIds.has(n.id) ? '<span class="dot" aria-label="未読">●</span>' : '<span class="dot"></span>'}
      ${a ? avatar(a, 36) : '<span class="avatar-ph">🎯</span>'}
      <span class="n-body">${notificationText(n)} <span class="sub">· ${relTime(n.createdAt)}</span>
      ${pending ? `<span class="n-actions"><button class="btn btn-primary btn-sm" data-approve="${n.actorId}">承認</button><button class="btn btn-sm" data-reject="${n.actorId}">拒否</button></span>` : ''}</span></a>`;
  };
  mountInfinite($('#nlist', root), list, render, { empty: '<p class="empty">通知はまだありません</p>', end: '' });
  root.addEventListener('click', async (e) => {
    const a = e.target.dataset.approve, r = e.target.dataset.reject;
    if (!a && !r) return;
    e.preventDefault();
    if (a) { approveRequest(me.id, Number(a)); toast('リクエストを承認しました'); }
    if (r && !(await confirmDialog({ title: 'リクエストを拒否しますか？', ok: '拒否する', danger: true }))) return;
    if (r) rejectRequest(me.id, Number(r));
    e.target.closest('.n-actions').remove();
    refreshNav();
  });
  refreshNav();
}

/* ===================== S-50 設定 ===================== */
function screenSettings(root) {
  const me = currentUser();
  const change = pendingEmailChange(me.id);
  const radio = (name, v, label, cur) => `<label class="radio"><input type="radio" name="${name}" value="${v}" ${cur === v ? 'checked' : ''}> ${label}</label>`;
  root.innerHTML = page('設定', `
    <section class="card"><h2 class="section-title">単位</h2>
      <div class="row">重量 ${radio('weightUnit', 'kg', 'kg', me.weightUnit)} ${radio('weightUnit', 'lb', 'lb', me.weightUnit)}</div>
      <div class="row">距離 ${radio('distanceUnit', 'km', 'km', me.distanceUnit)} ${radio('distanceUnit', 'mi', 'マイル', me.distanceUnit)}</div></section>
    <section class="card"><h2 class="section-title">プライバシー</h2>
      <label class="switch-row"><span>鍵アカウント</span><input type="checkbox" role="switch" id="priv" ${me.isPrivate ? 'checked' : ''}></label>
      <p class="sub">オンにすると、フォローを承認制にします。</p></section>
    <section class="card"><h2 class="section-title">アカウント</h2>
      <div class="row between"><span>メールアドレス ${esc(me.email)}（${me.emailVerified ? '確認済み' : '未確認'}）${change ? `<br><span class="sub">変更の確認待ち：${esc(change.email)}</span>` : ''}</span>
        <span>${!me.emailVerified || change ? '<button class="btn btn-sm" data-action="resend-verify">確認メールを再送</button>' : ''}<button class="btn btn-sm" id="chmail">変更</button></span></div>
      <div class="row between"><span>パスワード</span><button class="btn btn-sm" id="chpw">変更</button></div>
      <div class="row between"><span>プロフィール</span><a class="btn btn-sm" href="#/settings/profile">編集 ›</a></div></section>
    <div class="stack"><button class="btn" data-action="logout">ログアウト</button><a class="btn btn-danger-outline" href="#/settings/withdraw">退会する</a></div>`, { backTo: '#/' });
  $$('input[type=radio]', root).forEach((r) => r.addEventListener('change', () => { me[r.name] = r.value; toast('単位を変更しました'); rerender(); }));
  $('#priv', root).addEventListener('change', async (e) => {
    const on = e.target.checked;
    const n = pendingRequests(me.id).length;
    const ok = await confirmDialog(on
      ? { title: '鍵アカウントにしますか？', message: '承認したフォロワーだけが、あなたの記録と目標を見られるようになります。今のフォロワーはそのままです', ok: '鍵アカウントにする' }
      : { title: '鍵アカウントをやめますか？', message: `承認待ちのリクエスト（${n}件）はすべて承認されます`, ok: 'やめる' });
    if (!ok) { e.target.checked = !on; return; }
    me.isPrivate = on;
    if (!on) pendingRequests(me.id).forEach((f) => approveRequest(me.id, f.followerId));
    toast(on ? '鍵アカウントにしました' : '鍵アカウントをやめました');
    rerender();
  });
  $('#chmail', root).addEventListener('click', async () => {
    const v = await formDialog({
      title: 'メールアドレスの変更', fields: [{ name: 'email', label: '新しいメールアドレス', type: 'email' }, { name: 'password', label: '今のパスワード', type: 'password' }], ok: '確認のメールを送る',
      validate: (x) => {
        const e = x.email.trim().toLowerCase();
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e)) return 'メールアドレスの形式が正しくありません';
        if (e === me.email) return '今のメールアドレスと同じです';
        if (x.password !== me.password) return 'パスワードが正しくありません';
        if (db.users.some((u) => u.email === e)) return 'このメールアドレスはすでに使われています';
        return null;
      },
    });
    if (!v) return;
    if (!issueVerifyMail(me, v.email.trim().toLowerCase())) { toast('送信の上限（1時間に5通）を超えました。しばらくしてからお試しください'); return; }
    toast('新しいメールアドレスに確認のメールを送りました。リンクを開くと変更されます', 5000);
    rerender();
  });
  $('#chpw', root).addEventListener('click', async () => {
    const v = await formDialog({
      title: 'パスワードの変更', ok: '変更する',
      fields: [{ name: 'cur', label: '今のパスワード', type: 'password' }, { name: 'p1', label: '新しいパスワード（8〜72文字）', type: 'password' }, { name: 'p2', label: '新しいパスワード（確認）', type: 'password' }],
      validate: (x) => (x.cur !== me.password ? '今のパスワードが正しくありません' : x.p1.length < 8 || x.p1.length > 72 ? '新しいパスワードは8〜72文字にしてください' : x.p1 !== x.p2 ? '新しいパスワードが一致しません' : null),
    });
    if (!v) return;
    me.password = v.p1;
    toast('パスワードを変更しました。ほかの端末ではログアウトされます');
  });
}
function screenWithdraw(root) {
  const me = currentUser();
  root.innerHTML = page('退会', `<form id="wd" class="card" novalidate>
    <p class="warn">退会すると、記録・体重・目標などすべてのデータが削除され、元に戻せません。</p>
    <label class="field"><span class="field-label">パスワード</span><input type="password" name="pw"></label><p class="error" id="wderr"></p>
    <div class="form-foot"><a class="btn" href="#/settings">キャンセル</a><button class="btn btn-danger">退会する</button></div></form>`, { backTo: '#/settings' });
  $('#wd', root).addEventListener('submit', (e) => {
    e.preventDefault();
    if (e.target.pw.value !== me.password) { $('#wderr', root).textContent = 'パスワードが正しくありません'; return; }
    const id = me.id;
    db.workouts.filter((w) => w.userId === id).forEach((w) => deleteWorkout(w.id));
    db.comments.filter((c) => c.userId === id).forEach((c) => deleteComment(c.id));
    db.likes = db.likes.filter((l) => l.userId !== id);
    db.follows = db.follows.filter((f) => f.followerId !== id && f.followeeId !== id);
    db.notifications = db.notifications.filter((n) => n.recipientId !== id && n.actorId !== id);
    const goalIds = db.goals.filter((g) => g.userId === id).map((g) => g.id);
    db.goals = db.goals.filter((g) => g.userId !== id);
    db.goalCheers = db.goalCheers.filter((c) => c.userId !== id && !goalIds.includes(c.goalId));
    db.goalAchievements = db.goalAchievements.filter((a) => !goalIds.includes(a.goalId));
    db.bodyRecords = db.bodyRecords.filter((b) => b.userId !== id);
    db.templates = db.templates.filter((t) => t.userId !== id);
    db.exercises = db.exercises.filter((x) => x.ownerId !== id);
    db.users = db.users.filter((u) => u.id !== id);
    session.meId = null;
    go('#/login?msg=' + encodeURIComponent('退会しました'));
  });
}

/* ===================== モックだけの画面：届いたメール ===================== */
function screenMailbox(root) {
  root.innerHTML = page('📬 モックのメール', `<p class="sub">本番ではメールで届く確認用・再設定用のリンクを、ここで開けます（新しい順）。</p>
    <div class="list">${db.mails.length ? db.mails.map((m) => `<div class="card mail"><p class="sub">宛先：${esc(m.to)} · ${relTime(m.sentAt)}</p><h3>${esc(m.subject)}</h3><p>${esc(m.body)}</p><a class="btn btn-sm" href="${m.link}">リンクを開く</a></div>`).join('') : '<p class="empty">まだメールはありません</p>'}</div>`, { backTo: '#/' });
}
