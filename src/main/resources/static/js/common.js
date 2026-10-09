/* 通用：鉴权、请求、侧边栏、工具 */
const TOKEN_KEY = 'dw_token';

function getToken() { return localStorage.getItem(TOKEN_KEY); }

async function api(path, opts = {}) {
  const res = await fetch(path, {
    ...opts,
    headers: { 'Content-Type': 'application/json', 'X-Token': getToken() || '', ...(opts.headers || {}) },
  });
  if (res.status === 401) {
    localStorage.removeItem(TOKEN_KEY);
    location.href = '/login.html';
    throw new Error('unauthorized');
  }
  const body = await res.json();
  if (body.code !== 0) throw new Error(body.msg || '请求失败');
  return body.data;
}

function requireLogin() {
  if (!getToken()) location.href = '/login.html';
}

function logout() {
  api('/api/auth/logout', { method: 'POST' }).finally(() => {
    localStorage.removeItem(TOKEN_KEY);
    location.href = '/login.html';
  });
}

const NAVS = [
  ['index.html', '实时监控'],
  ['alerts.html', '告警中心'],
  ['devices.html', '设备管理'],
  ['rules.html', '规则管理'],
  ['stats.html', '统计分析'],
];

function renderLayout(active, title) {
  const cur = location.pathname.split('/').pop() || 'index.html';
  document.body.innerHTML = `
  <div class="layout">
    <div class="sidebar">
      <div class="logo">Device<span>Watch</span></div>
      <div class="nav">${NAVS.map(([h, n]) =>
        `<a href="/${h}" class="${h === cur ? 'active' : ''}">${n}</a>`).join('')}</div>
    </div>
    <div class="main">
      <div class="topbar">
        <h1>${title}</h1>
        <div class="user"><span id="uname"></span><button class="btn small" onclick="logout()">退出</button></div>
      </div>
      <div id="content"></div>
    </div>
  </div>
  <div id="toast"></div>
  <div class="modal-mask" id="modalMask"><div class="modal" id="modalBox"></div></div>`;
  api('/api/auth/me').then(me => {
    document.getElementById('uname').textContent = me.realName || me.username;
  }).catch(() => {});
  return document.getElementById('content');
}

function toast(msg, ms = 2200) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.style.display = 'block';
  clearTimeout(t._timer);
  t._timer = setTimeout(() => t.style.display = 'none', ms);
}

function openModal(html) {
  document.getElementById('modalBox').innerHTML = html;
  document.getElementById('modalMask').classList.add('show');
}
function closeModal() {
  document.getElementById('modalMask').classList.remove('show');
}
document.addEventListener('click', e => {
  if (e.target.id === 'modalMask') closeModal();
});

function fmtTime(s) {
  if (!s) return '-';
  return s.replace('T', ' ').substring(0, 19);
}
function fmtVal(v) {
  if (v == null || v === '') return '-';
  const n = Number(v);
  if (!isFinite(n)) return String(v);
  return String(parseFloat(n.toFixed(3)));
}
function levelBadge(l) {
  return l === 'CRITICAL' ? '<span class="badge b-critical">严重</span>' : '<span class="badge b-warn">一般</span>';
}
function statusBadge(s) {
  if (s === 'FIRING') return '<span class="badge b-red">告警中</span>';
  if (s === 'ACKED') return '<span class="badge b-blue">已确认</span>';
  return '<span class="badge b-gray">已关闭</span>';
}
function devStatusBadge(s) {
  if (s === 'ONLINE') return '<span class="status-dot dot-online"></span>在线';
  if (s === 'ALARM') return '<span class="status-dot dot-alarm"></span>告警';
  return '<span class="status-dot dot-offline"></span>离线';
}
function esc(s) {
  return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
