/**
 * API 层
 *
 * 两个来源，经 Nginx 同源代理，因此前端不涉及任何跨域：
 *   /api/**       → gym-management-system 业务后端（Java 8 / SpringBoot 2.7）
 *   /agent-api/** → fitness-agent 智能体（Java 17 / SpringBoot 3.2）
 *
 * 智能体前缀刻意不叫 /agent：前端 SPA 路由已占用 /agent 这个页面地址，
 * 代理前缀若同名，nginx 会把 "/agent" 301 到 "/agent/" 再转发给后端，页面就打不开。
 *
 * 注意业务后端的接口存在两种入参风格，必须区别对待：
 *   · Member / Employee / Equipment / Register / Recharge / CheckIn 的增改接收表单参数
 *   · Course 的增改接收 JSON（@RequestBody）
 * 混用会静默丢字段，因此这里把它们显式区分成 form / json 两组方法。
 *
 * 请求智能体时统一带上两类请求头：
 *   · 归属（token / X-Client-Id）—— 服务端据此把会话记录隔离到"谁"
 *   · 大模型配置（X-LLM-*）      —— 用户自己填的密钥，服务端不保存
 */
import { session } from './auth.js';

const GYM = '/api';
const AGENT = '/agent-api';

/* ============================================================
   本地设置（只存在浏览器里，不上传、不落库）
   ============================================================ */

const KEY_LLM = 'fitness.llm.v1';
const KEY_LAN = 'fitness.lanBase.v1';

/** 大模型设置：用户自己填，随请求头下发给智能体 */
export function getLlmSettings() {
  try {
    return JSON.parse(localStorage.getItem(KEY_LLM) || '{}') || {};
  } catch (_) {
    return {};
  }
}

export function setLlmSettings(cfg) {
  const clean = {};
  ['baseUrl', 'apiKey', 'model'].forEach((k) => {
    const v = (cfg && cfg[k]) || '';
    if (String(v).trim()) clean[k] = String(v).trim();
  });
  localStorage.setItem(KEY_LLM, JSON.stringify(clean));
}

export function clearLlmSettings() {
  localStorage.removeItem(KEY_LLM);
}

/** 请求头：把用户自己填的配置带给智能体（服务端不保存密钥） */
function llmHeaders() {
  const cfg = getLlmSettings();
  const h = {};
  if (cfg.apiKey) h['X-LLM-Api-Key'] = cfg.apiKey;
  if (cfg.baseUrl) h['X-LLM-Base-Url'] = cfg.baseUrl;
  if (cfg.model) h['X-LLM-Model'] = cfg.model;
  return h;
}

/* ============================================================
   归属标识（会话数据隔离的凭据）
   ============================================================ */

const KEY_CLIENT = 'fitness.clientId.v1';

/**
 * 浏览器本地标识。
 *
 * 未登录访客的身份无法验证，只能靠浏览器本地生成的一个随机串区分，
 * 因此它的隔离范围是「同一浏览器的同一份本地存储」，而不是「某个人」。
 * 服务端也据此把这类归属标记为「未验证」，不冒充账号级隔离。
 */
export function clientId() {
  let v = '';
  try { v = localStorage.getItem(KEY_CLIENT) || ''; } catch (_) { v = ''; }
  if (!v || !/^[A-Za-z0-9_-]{8,64}$/.test(v)) {
    v = 'g' + Math.random().toString(36).slice(2, 12) + Date.now().toString(36);
    try { localStorage.setItem(KEY_CLIENT, v); } catch (_) { /* 隐私模式忽略 */ }
  }
  return v;
}

/**
 * 归属请求头。登录令牌优先——服务端会拿它去签发方换成可验证的身份；
 * 未登录则带浏览器标识，服务端按「未验证访客」隔离。
 * 两者都不带时服务端不记录会话（宁可不存，也不落进一个"公共桶"）。
 */
export function ownerHeaders() {
  const h = { 'X-Client-Id': clientId() };
  if (session && session.token) h.token = session.token;
  return h;
}

/** 智能体请求头 = 归属 + 用户自己的大模型配置 */
function agentHeaders() {
  return Object.assign(ownerHeaders(), llmHeaders());
}

/**
 * 局域网地址：手机扫码要能打开，就必须是手机可达的地址（localhost 不行）。
 * 默认取当前访问地址；如果当前是 localhost，则由用户在设置里填本机局域网 IP。
 */
export function getLanBase() {
  const saved = localStorage.getItem(KEY_LAN);
  if (saved) return saved.replace(/\/$/, '');
  return '';
}

export function setLanBase(v) {
  if (v && String(v).trim()) {
    localStorage.setItem(KEY_LAN, String(v).trim().replace(/\/$/, ''));
  } else {
    localStorage.removeItem(KEY_LAN);
  }
}

/** 把一个站内相对路径拼成手机扫得到的绝对地址 */
export function absoluteUrl(path) {
  const base = getLanBase() || window.location.origin;
  return base.replace(/\/$/, '') + path;
}

/** 把对象拼成查询串，跳过 null / undefined / 空串 */
function qs(params = {}) {
  const usp = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== null && v !== undefined && v !== '') usp.append(k, v);
  });
  const s = usp.toString();
  return s ? `?${s}` : '';
}

async function raise(res) {
  let message = `请求失败（HTTP ${res.status}）`;
  try {
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('json')) {
      const body = await res.json();
      // 智能体的统一错误契约与业务系统的 Spring 默认错误结构都要兼容
      message = body.message || body.error || message;
    } else {
      const text = await res.text();
      if (text) message = text.slice(0, 160);
    }
  } catch (_) { /* 响应体不可解析时沿用默认文案 */ }
  const err = new Error(message);
  err.status = res.status;
  throw err;
}

async function parse(res) {
  if (!res.ok) return raise(res);
  if (res.status === 204) return null;
  const ct = res.headers.get('content-type') || '';
  if (!ct.includes('json')) {
    const text = await res.text();
    return text ? text : null;
  }
  try {
    return await res.json();
  } catch (_) {
    return null;
  }
}

const http = {
  get(url) {
    return fetch(url, { headers: { Accept: 'application/json' } }).then(parse);
  },
  /** 带自定义头的 GET（归属请求头等） */
  getWith(url, headers) {
    return fetch(url, {
      headers: Object.assign({ Accept: 'application/json' }, headers || {}),
    }).then(parse);
  },
  /** DELETE */
  del(url, headers) {
    return fetch(url, {
      method: 'DELETE',
      headers: Object.assign({ Accept: 'application/json' }, headers || {}),
    }).then(parse);
  },
  /** 表单参数提交 */
  postForm(url, data) {
    const body = new URLSearchParams();
    Object.entries(data || {}).forEach(([k, v]) => {
      if (v !== null && v !== undefined) body.append(k, v);
    });
    return fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', Accept: 'application/json' },
      body,
    }).then(parse);
  },
  /** JSON 提交；headers 用于给智能体带上用户自己填的大模型配置 */
  postJson(url, data, headers) {
    return fetch(url, {
      method: 'POST',
      headers: Object.assign(
        { 'Content-Type': 'application/json;charset=UTF-8', Accept: 'application/json' },
        headers || {},
      ),
      body: JSON.stringify(data || {}),
    }).then(parse);
  },
};

/* 分页约定：业务后端的 page 是「偏移量」而非页码（SQL 为 limit #{page},#{size}），
   因此页码从 1 开始时，偏移量为 (页码 - 1) * 每页条数。 */
export const pageOffset = (pageNo, pageSize) => (pageNo - 1) * pageSize;

/* ============================================================
   业务系统 API
   ============================================================ */
export const gymApi = {
  /* ---------- 鉴权 ---------- */
  adminLogin: (adminAccount, adminPassword) =>
    http.get(`${GYM}/getAdminPassword${qs({ adminAccount, adminPassword })}`),
  memberLogin: (memberPhone, memberPassword) =>
    http.get(`${GYM}/getMemberPassword${qs({ memberPhone, memberPassword })}`),
  checkToken: (token) =>
    fetch(`${GYM}/checkToken`, { headers: { token } }).then(parse),

  /* ---------- 会员 ---------- */
  memberList: (page, size) => http.get(`${GYM}/getAllMember${qs({ page, size })}`),
  memberSearch: (keyWord, page, size) => http.get(`${GYM}/getByKeywordMember${qs({ keyWord, page, size })}`),
  memberTotal: () => http.get(`${GYM}/totalMember`),
  memberTotalFuzzy: (keyWord) => http.get(`${GYM}/totalMemberFuzzy${qs({ keyWord })}`),
  memberById: (memberNo) => http.get(`${GYM}/getMemberByMemberNo${qs({ memberNo })}`),
  memberCreate: (data) => http.postForm(`${GYM}/addMember`, data),
  memberUpdate: (data) => http.postForm(`${GYM}/updateMember`, data),
  memberDelete: (memberNo) => http.postForm(`${GYM}/deleteMember`, { memberNo }),
  memberIntegral: (memberNo) => http.get(`${GYM}/getMemberIntegral${qs({ memberNo })}`),
  memberBalance: (memberNo) => http.get(`${GYM}/getMemberChange${qs({ memberNo })}`),
  memberTotalMoney: (memberNo) => http.get(`${GYM}/getTotalMoney${qs({ memberNo })}`),
  memberUpdatePassword: (memberPhone, memberPassword) =>
    http.postForm(`${GYM}/updateMemberPassword`, { memberPhone, memberPassword }),
  memberExportUrl: () => `${GYM}/memberExport`,

  /* ---------- 课程（JSON 入参） ---------- */
  courseList: (page, size) => http.get(`${GYM}/getAllCourse${qs({ page, size })}`),
  courseSearch: (keyWord, page, size) => http.get(`${GYM}/getByKeywordCourse${qs({ keyWord, page, size })}`),
  courseTotal: () => http.get(`${GYM}/totalCourse`),
  courseTotalFuzzy: (keyWord) => http.get(`${GYM}/totalCourseFuzzy${qs({ keyWord })}`),
  courseCreate: (data) => http.postJson(`${GYM}/addCourse`, data),
  courseUpdate: (data) => http.postJson(`${GYM}/updateCourse`, data),
  courseDelete: (courseNo) => http.postForm(`${GYM}/deleteCourse`, { courseNo }),

  /* ---------- 器材（表单入参） ---------- */
  equipmentList: (page, size) => http.get(`${GYM}/getAllEquipment${qs({ page, size })}`),
  equipmentSearch: (keyWord, page, size) => http.get(`${GYM}/getByKeywordEquipment${qs({ keyWord, page, size })}`),
  equipmentTotal: () => http.get(`${GYM}/totalEquipment`),
  equipmentTotalFuzzy: (keyWord) => http.get(`${GYM}/totalEquipmentFuzzy${qs({ keyWord })}`),
  equipmentCreate: (data) => http.postForm(`${GYM}/addEquipment`, data),
  equipmentUpdate: (data) => http.postForm(`${GYM}/updateEquipment`, data),
  equipmentDelete: (equipmentNo) => http.postForm(`${GYM}/deleteEquipment`, { equipmentNo }),

  /* ---------- 员工（表单入参） ---------- */
  employeeList: (page, size) => http.get(`${GYM}/getAllEmployee${qs({ page, size })}`),
  employeeSearch: (keyWord, page, size) => http.get(`${GYM}/getByKeywordEmployee${qs({ keyWord, page, size })}`),
  employeeTotal: () => http.get(`${GYM}/totalEmployee`),
  employeeTotalFuzzy: (keyWord) => http.get(`${GYM}/totalEmployeeFuzzy${qs({ keyWord })}`),
  employeeCreate: (data) => http.postForm(`${GYM}/addEmployee`, data),
  employeeUpdate: (data) => http.postForm(`${GYM}/updateEmployee`, data),
  employeeDelete: (employeeNo) => http.postForm(`${GYM}/deleteEmployee`, { employeeNo }),
  coachList: () => http.get(`${GYM}/getAllCoach`),
  managerList: () => http.get(`${GYM}/getAllManager`),

  /* ---------- 报名（表单入参） ---------- */
  registerList: (page, size) => http.get(`${GYM}/getAllRegister${qs({ page, size })}`),
  registerSearch: (keyWord, page, size) => http.get(`${GYM}/getByKeywordRegister${qs({ keyWord, page, size })}`),
  registerTotal: () => http.get(`${GYM}/totalRegister`),
  registerTotalFuzzy: (keyWord) => http.get(`${GYM}/totalRegisterFuzzy${qs({ keyWord })}`),
  registerCreate: (data) => http.postForm(`${GYM}/addRegister`, data),
  registerUpdate: (data) => http.postForm(`${GYM}/updateRegister`, data),
  registerDelete: (registerNo) => http.postForm(`${GYM}/deleteRegister`, { registerNo }),
  registerByMember: (memberNo) => http.get(`${GYM}/getRegisterByMemberNo${qs({ memberNo })}`),

  /* ---------- 充值 ---------- */
  rechargeByMember: (memberNo) => http.get(`${GYM}/getRechargeByMemberNo${qs({ memberNo })}`),
  rechargeCreate: (data) => http.postForm(`${GYM}/addRechargeByMemberNo`, data),

  /* ---------- 签到 ---------- */
  checkInCreate: (data) => http.postForm(`${GYM}/addCheckIn`, data),

  /* ---------- 统计 ---------- */
  memberBySeason: (year) => http.get(`${GYM}/echarts/getMemberBySeason${qs({ year })}`),
  memberSexByYear: (year) => http.get(`${GYM}/echarts/getMemberSexByYear${qs({ year })}`),
};

/* ============================================================
   智能体 API
   ============================================================ */
export const agentApi = {
  metadata: () => http.get(`${AGENT}/qa/metadata`),

  /**
   * 对话式主入口：提交对话历史 + 待办 + 会话标识。
   * 服务端不保存"运行状态"（待办由前端回传），但会把历史落到智能体自己的库；
   * 响应里的 sessionKey 带回来即续上同一会话。
   */
  chat: (payload) => http.postJson(`${AGENT}/qa/chat`, payload, agentHeaders()),

  /** 兼容保留：表单式直接生成计划 */
  fitnessPlan: (payload) => http.postJson(`${AGENT}/qa/fitness-plan`, payload, agentHeaders()),

  /** 订单状态：电脑端轮询「手机上是否已付款」 */
  orderStatus: (orderNo) => http.get(`${AGENT}/qa/pay/${orderNo}`),

  /** 确认支付（演示为模拟支付），支付页调用 */
  confirmPay: (orderNo) => http.postJson(`${AGENT}/qa/pay/${orderNo}/confirm`, {}),

  /* ---------- 会话（历史记录 + 归属隔离） ---------- */
  sessions: () => http.getWith(`${AGENT}/qa/sessions`, ownerHeaders()),
  createSession: (title) => http.postJson(`${AGENT}/qa/sessions`, { title: title || '' }, ownerHeaders()),
  sessionMessages: (key) => http.getWith(`${AGENT}/qa/sessions/${encodeURIComponent(key)}/messages`, ownerHeaders()),
  renameSession: (key, title) =>
    http.postJson(`${AGENT}/qa/sessions/${encodeURIComponent(key)}/rename`, { title }, ownerHeaders()),
  deleteSession: (key) => http.del(`${AGENT}/qa/sessions/${encodeURIComponent(key)}`, ownerHeaders()),
  storageStats: () => http.getWith(`${AGENT}/qa/sessions/_storage`, ownerHeaders()),

  /* ---------- 知识库（浏览与检索，供页面与排查用） ---------- */
  knowledgeStats: () => http.get(`${AGENT}/knowledge/stats`),
  knowledgeDocs: (category) => http.get(`${AGENT}/knowledge/docs${qs({ category })}`),
  knowledgeDoc: (id) => http.get(`${AGENT}/knowledge/docs/${encodeURIComponent(id)}`),
  knowledgeSearch: (q, topK) => http.get(`${AGENT}/knowledge/search${qs({ q, topK })}`),
};

export default { gymApi, agentApi, pageOffset, clientId, ownerHeaders };
