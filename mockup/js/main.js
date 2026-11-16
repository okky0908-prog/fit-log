'use strict';
/* FIT LOG モックアップ：画面の切り替え（URL のハッシュ #/...）と、画面をまたぐ操作 */

const ROUTES = [
  ['/login', screenLogin, 'guest'], ['/signup', screenSignup, 'guest'], ['/password-reset', screenResetRequest, 'guest'],
  ['/password-reset/confirm', screenResetConfirm, 'any'], ['/verify-email', screenVerify, 'any'], ['/mailbox', screenMailbox, 'any'],
  ['/', screenHome], ['/all', screenHome], ['/search', screenSearch], ['/search/users', screenSearch],
  ['/workouts/new', screenEditor], ['/workouts/:id/edit', screenEditor], ['/workouts/:id', screenDetail],
  ['/templates', screenTemplates], ['/templates/new', screenEditor], ['/templates/:id/edit', screenEditor],
  ['/records', screenDashboard], ['/records/month', screenDashboard], ['/records/running', screenRunning],
  ['/records/exercises', screenTrend], ['/records/exercises/:id', screenTrend],
  ['/goals', screenGoals], ['/goals/new', screenGoalEdit], ['/goals/:id/edit', screenGoalEdit], ['/body', screenBody],
  ['/notifications', screenNotifications], ['/users/:username', screenProfile],
  ['/users/:username/following', screenFollowList], ['/users/:username/followers', screenFollowList],
  ['/follow-requests', screenRequests], ['/settings', screenSettings], ['/settings/profile', screenEditProfile], ['/settings/withdraw', screenWithdraw],
].map(([pattern, fn, access]) => {
  const keys = [];
  const re = new RegExp('^' + pattern.replace(/:(\w+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '$');
  return { re, keys, fn, access };
});

function parseHash() {
  const raw = location.hash.slice(1) || '/';
  const [path, query = ''] = raw.split('?');
  return { path: decodeURI(path) || '/', q: new URLSearchParams(query) };
}
function render() {
  const { path, q } = parseHash();
  const me = currentUser();
  let route = null, params = {};
  for (const r of ROUTES) {
    const m = path.match(r.re);
    if (m) { route = r; r.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); }); break; }
  }
  if (!route) { location.replace(me ? '#/' : '#/login'); return; }
  if (!me && !route.access) { location.replace('#/login?next=' + encodeURIComponent(location.hash)); return; }
  if (me && route.access === 'guest') { location.replace('#/'); return; }
  renderMockBar();
  $('#nav').innerHTML = me ? renderNav(me, path) : '';
  document.body.classList.toggle('logged-in', !!me);
  const screen = el('<div class="screen"></div>');
  $('#main').replaceChildren(screen);
  route.fn(screen, params, q, path);
  document.title = `${$('h1', screen)?.textContent.trim() || 'FIT LOG'} | FIT LOG（モックアップ）`;
}
/** 今の画面を描き直す（スクロールの位置は保つ） */
function rerender() {
  const y = window.scrollY;
  render();
  window.scrollTo(0, y);
}
function refreshNav() {
  const me = currentUser();
  if (!me || $('#nav .menu:not([hidden])')) return;
  $('#nav').innerHTML = renderNav(me, parseHash().path);
}
function renderMockBar() {
  const me = currentUser();
  $('#mockbar').innerHTML = `<span>🧪 モックアップ：データはこのタブの中だけ（再読み込みで初期状態に戻ります）${me ? ` ／ ログイン中：${esc(me.displayName)}` : ''}</span>
    <span><a href="#/mailbox">📬 モックのメール（${db.mails.length}）</a>${me ? ' <button class="link-btn" data-action="mock-post">👟 仲間が投稿する</button>' : ''}</span>`;
}

/* ===================== 画面をまたぐ操作（data-action） ===================== */
const isTouch = () => matchMedia('(hover: none)').matches;
const ACTIONS = {
  back: (t) => back(t.dataset.fallback),
  home: (t, e) => {
    const target = t.getAttribute('href') || '#/';
    const now = location.hash || '#/';
    if (target === now || (target === '#/' && now === '#')) { e.preventDefault(); render(); window.scrollTo(0, 0); }
  },
  'toggle-menu': (t) => {
    const menu = t.parentElement.querySelector('.menu');
    const open = menu.hidden;
    closeMenus();
    menu.hidden = !open;
    t.setAttribute('aria-expanded', String(open));
  },
  like: (t) => {
    // 押した瞬間に画面を先に変える（API が失敗したら元に戻す。モックでは失敗しない）
    const id = Number(t.dataset.id);
    toggleLike(id, session.meId);
    const n = db.likes.filter((l) => l.workoutId === id).length;
    const liked = db.likes.some((l) => l.workoutId === id && l.userId === session.meId);
    $$(`.like[data-id="${id}"]`).forEach((b) => { b.classList.toggle('liked', liked); b.setAttribute('aria-pressed', liked); $('span', b).textContent = n || ''; });
  },
  follow: (t) => {
    const u = userById(Number(t.dataset.user));
    follow(session.meId, u.id);
    toast(u.isPrivate ? 'フォローをリクエストしました' : `${u.displayName}さんをフォローしました`);
    rerender();
  },
  unfollow: async (t) => {
    const u = userById(Number(t.dataset.user));
    if (isTouch() && !(await confirmDialog({ title: 'フォローを解除しますか？', ok: '解除する', danger: true }))) return;
    unfollow(session.meId, u.id); rerender();
  },
  'cancel-request': async (t) => {
    if (!(await confirmDialog({ title: 'リクエストを取り消しますか？', ok: '取り消す' }))) return;
    unfollow(session.meId, Number(t.dataset.user)); rerender();
  },
  'delete-workout': async (t) => {
    if (!(await confirmDialog({ title: '記録を削除しますか？', message: 'コメント・ナイストレーニングも削除され、元に戻せません', ok: '削除する', danger: true }))) return;
    deleteWorkout(Number(t.dataset.id));
    toast('記録を削除しました');
    if (parseHash().path.startsWith('/workouts/')) go('#/'); else rerender();
  },
  'save-template': async (t) => {
    const w = workoutById(Number(t.dataset.id));
    if (db.templates.filter((x) => x.userId === session.meId).length >= 20) { toast('テンプレートは20件までです'); return; }
    const v = await formDialog({ title: 'テンプレートとして保存', message: 'この記録の種目とセットを保存します', fields: [{ name: 'name', label: '名前（1〜30文字）' }],
      validate: (x) => (charCount(x.name) < 1 || charCount(x.name) > 30 ? '名前は1〜30文字で入力してください' : null) });
    if (!v) return;
    db.templates.push({ id: nextId('templates'), userId: session.meId, name: v.name.trim(), exercises: structuredClone(w.exercises), updatedAt: Date.now() });
    toast('テンプレートに保存しました');
  },
  lightbox: (t) => {
    const imgs = $$('img', t.closest('.images')).map((i) => i.src);
    lightbox(imgs, Number(t.dataset.index));
  },
  cheer: (t) => {
    const on = toggleCheer(Number(t.dataset.id), session.meId);
    toast(on ? '応援しました' : '応援を取り消しました');
    rerender();
  },
  logout: () => { session.meId = null; go('#/login'); },
  'resend-verify': () => {
    const me = currentUser();
    if (!me) return;
    const change = pendingEmailChange(me.id);
    if (me.emailVerified && !change) { toast('メールアドレスは確認済みです'); return; }
    const ok = issueVerifyMail(me, change ? change.email : me.email);
    toast(ok ? `確認のメールを送りました（${change ? change.email : me.email}）` : '送信の上限（1時間に5通）を超えました。しばらくしてからお試しください');
    renderMockBar();
  },
  'hide-verify': () => { sessionStorage.setItem('hideVerify', todayStr()); refreshNav(); },
  'mock-post': () => {
    // 「新しい投稿のお知らせ」を試すため、フォロー中の仲間の投稿を1件作る
    const me = currentUser();
    const friend = db.users.find((u) => u.id !== me.id && isAccepted(me.id, u.id) && !u.isPrivate) || db.users.find((u) => u.id !== me.id);
    const km = [3, 5, 8, 10][Math.floor(Math.random() * 4)];
    db.workouts.push({ id: nextId('workouts'), userId: friend.id, performedOn: todayStr(), memo: `${km}km 走ってきました #ランニング`, durationMin: null, visibility: 'public',
      createdAt: Date.now(), editedAt: null, images: [], exercises: [{ exerciseId: exerciseByName('ランニング').id, sets: [{ weightKg: null, reps: null, durationSec: km * 330, distanceKm: km }] }] });
    toast(`${friend.displayName}さんが投稿しました（ホームでは件数のお知らせだけが出ます）`);
    checkNewPosts();
  },
};
function closeMenus(except) {
  $$('.menu').forEach((m) => { if (m !== except) m.hidden = true; });
}
document.addEventListener('click', (e) => {
  const actionEl = e.target.closest('[data-action]');
  if (actionEl && ACTIONS[actionEl.dataset.action]) {
    if (actionEl.tagName === 'BUTTON') e.preventDefault();
    if (actionEl.dataset.action !== 'toggle-menu') closeMenus();
    ACTIONS[actionEl.dataset.action](actionEl, e);
    return;
  }
  if (!e.target.closest('.menu-wrap')) closeMenus();
  else if (e.target.closest('.menu a, .menu button')) closeMenus();
  // 投稿カードの余白を押すと S-12 へ（リンク・ボタン・[data-stop] の中は除く）
  const card = e.target.closest('.workout-card');
  if (card && !e.target.closest('a, button, input, textarea, [data-stop]')) go(card.dataset.href);
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && e.target.classList?.contains('workout-card')) go(e.target.dataset.href);
  if (e.key === 'Escape') closeMenus();
});
window.addEventListener('hashchange', render);
// 新しい投稿の件数・未読数の確認（本番は60秒ごと。モックでは確かめやすいよう10秒ごと）
setInterval(() => { checkNewPosts(); refreshNav(); }, 10000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) { checkNewPosts(); refreshNav(); } });

seed();
render();
