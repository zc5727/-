/**
 * 会话状态。使用 localStorage 持久化，刷新不掉登录态。
 * 角色：admin（管理员）/ member（会员），路由守卫据此分流。
 */
const { reactive } = Vue;

const STORAGE_KEY = 'gym.session.v1';

function read() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch (_) {
    return null;
  }
}

const initial = read() || {
  role: null,
  token: null,
  account: null,
  name: null,
  memberNo: null,
  loginAt: null,
};

export const session = reactive({ ...initial });

export function setAdminSession(data) {
  Object.assign(session, {
    role: 'admin',
    token: data.token || null,
    account: data.adminAccount || null,
    name: data.adminAccount || '管理员',
    memberNo: null,
    loginAt: Date.now(),
  });
  persist();
}

export function setMemberSession(data) {
  Object.assign(session, {
    role: 'member',
    token: data.token || null,
    account: data.memberPhone || null,
    name: data.memberUsername || data.memberPhone || '会员',
    memberNo: data.memberNo != null ? Number(data.memberNo) : null,
    loginAt: Date.now(),
  });
  persist();
}

export function clearSession() {
  Object.assign(session, {
    role: null, token: null, account: null, name: null, memberNo: null, loginAt: null,
  });
  try { localStorage.removeItem(STORAGE_KEY); } catch (_) { /* 忽略隐私模式下的异常 */ }
}

function persist() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...session }));
  } catch (_) { /* 忽略 */ }
}

export const isAdmin = () => session.role === 'admin';
export const isMember = () => session.role === 'member';
export const isLoggedIn = () => !!session.role;

/** 取昵称首字，用于头像展示 */
export function initials(name) {
  if (!name) return 'U';
  const s = String(name).trim();
  return /^[\u4e00-\u9fa5]/.test(s) ? s.slice(0, 1) : s.slice(0, 2).toUpperCase();
}

export default { session, setAdminSession, setMemberSession, clearSession, isAdmin, isMember, isLoggedIn, initials };
