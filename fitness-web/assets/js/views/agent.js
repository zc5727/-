/**
 * 智能助手 —— 对话式交互页面（系统的核心）。
 *
 * 设计立场：这一页**没有表单**。用户说人话，Agent 自己决定是追问、查询、
 * 还是要摆一张卡出来。所谓"不呆板"，靠的就是卡片——文字负责说，卡片负责办：
 *   CONFIRM 确认卡 / PAY 二维码支付卡 / LIST 列表卡 / RECEIPT 回执卡 / NOTICE 能力清单
 *   SOURCE  来源卡（知识问答引用出处）
 *
 * 两份状态，分工明确：
 *   · pending    —— 上一轮留下的待办，决定了"好""200""我付好了"这些短答的含义；
 *                    属于"运行状态"，由前端保存并回传，服务端不留。
 *   · sessionKey —— 会话标识。历史落在智能体自己的库里，带回来即续上；
 *                    左侧列表可新建 / 切换 / 改名 / 删除。
 *
 * 归属隔离由请求头承载（token 优先，未登录用浏览器标识），前端不做任何"我是谁"的声明。
 */
import { agentApi, getLlmSettings, setLlmSettings, clearLlmSettings, getLanBase, setLanBase, absoluteUrl } from '../api.js';

const { ElMessage, ElMessageBox } = ElementPlus;

const CONFIRM_SEED = '确认';
const CANCEL_SEED = '算了';
const PAID_SEED = '我付好了';

export default {
  name: 'AgentChatPage',
  props: {
    /** 会员端传入：锁定会员身份，查询与充值都作用于该会员 */
    lockedMemberNo: { type: Number, default: null },
    /** 紧凑模式：用于会员中心内嵌 */
    compact: { type: Boolean, default: false },
  },
  data() {
    return {
      meta: null,
      messages: [],
      draft: '',
      sending: false,
      pending: null,
      settingsVisible: false,
      settings: { baseUrl: '', apiKey: '', model: '', lanBase: '' },
      modelLabel: '',
      serverLlmReady: false,
      pollTimer: null,
      pollCount: 0,
      receiptShownFor: null,
      /* ---------- 会话 ---------- */
      sessionKey: null,
      sessions: [],
      sessionsLoading: false,
      historyReady: true,
    };
  },
  computed: {
    isLocked() {
      return this.lockedMemberNo != null;
    },
    llmReady() {
      return !!this.settings.apiKey || this.serverLlmReady;
    },
    lanBase() {
      return getLanBase() || window.location.origin;
    },
    lanIsLocalhost() {
      return /localhost|127\.0\.0\.1/.test(this.lanBase);
    },
    quickPrompts() {
      return this.isLocked
        ? ['我卡里还有多少钱', '我充过几次', '充 200', '我想减脂']
        : ['我卡里还有多少钱', '我有多少积分', '深蹲膝盖疼怎么办', '我想减脂'];
    },
    currentSession() {
      return this.sessions.find((s) => s.sessionKey === this.sessionKey) || null;
    },
  },
  mounted() {
    const cfg = getLlmSettings();
    this.settings = {
      baseUrl: cfg.baseUrl || '',
      apiKey: cfg.apiKey || '',
      model: cfg.model || '',
      lanBase: getLanBase(),
    };
    this.loadMeta();
    this.bootstrapSession();
  },
  beforeUnmount() {
    this.stopPolling();
  },
  updated() {
    this.$nextTick(() => this.renderQrs());
  },
  methods: {
    async loadMeta() {
      try {
        this.meta = await agentApi.metadata();
        this.serverLlmReady = !!(this.meta && this.meta.llmConfigured);
        this.modelLabel = (this.meta && this.meta.llmModel) || 'deepseek-flash';
      } catch (_) {
        /* 元数据拿不到不影响对话 */
      }
    },

    /* ---------- 会话：列表 / 新建 / 切换 / 回放 ---------- */

    /** 进页面时：有历史就续上最近一条，没有就开个空会话等用户开口 */
    async bootstrapSession() {
      await this.loadSessions();
      if (this.sessions.length) {
        await this.openSession(this.sessions[0].sessionKey);
      } else {
        await this.startNewSession();
      }
    },

    async loadSessions() {
      this.sessionsLoading = true;
      try {
        const res = await agentApi.sessions();
        this.historyReady = !!(res && res.available);
        this.sessions = (res && res.sessions) || [];
      } catch (_) {
        // 历史不可用只是少了记忆，对话本身照常——不弹错误打扰用户
        this.historyReady = false;
      } finally {
        this.sessionsLoading = false;
      }
    },

    /**
     * 开一个新会话。
     *
     * <p>刻意先跟服务端要一句问候语：这样"开场白"只有服务端一个来源，
     * 前端不复制一份文案。此处不带用户发言，因此服务端不会凭空建出一条空会话记录——
     * 会话是在用户真正说出第一句话时才落库的。
     */
    async startNewSession() {
      this.stopPolling();
      this.sessionKey = null;
      this.pending = null;
      this.receiptShownFor = null;
      this.messages = [];
      try {
        const res = await this.call({ messages: [], pending: null });
        this.absorb(res);
      } catch (e) {
        this.pushAgent({ text: '助手暂时连不上：' + e.message });
      }
    },

    /** 回放一条历史会话：助手消息带着完整响应快照，卡片与计划都能原样重建 */
    async openSession(key) {
      if (!key) return;
      this.stopPolling();
      this.sessionKey = key;
      this.pending = null;
      this.receiptShownFor = null;
      this.messages = [];
      try {
        const data = await agentApi.sessionMessages(key);
        (data.messages || []).forEach((m) => this.messages.push(this.toUiMessage(m)));
        // 待办取最后一条助手消息的快照：不恢复它，续聊时「好」「我付好了」就失去了含义
        for (let i = this.messages.length - 1; i >= 0; i--) {
          if (this.messages[i].role === 'agent') {
            this.pending = this.messages[i].pending || null;
            break;
          }
        }
        // 会话若停在"待支付"，把轮询接回来，手机上付完这边照样到账
        for (let i = this.messages.length - 1; i >= 0; i--) {
          const cards = this.messages[i].cards || [];
          const pay = cards.find((c) => c.type === 'PAY');
          if (pay) {
            this.startPolling(pay.data.orderNo);
            break;
          }
          if (cards.some((c) => c.type === 'RECEIPT')) break;
        }
        this.scrollToBottom();
      } catch (e) {
        ElMessage.error('打开会话失败：' + e.message);
      }
    },

    /** 库里的一条消息 → 界面的一条消息 */
    toUiMessage(m) {
      if (m.role === 'user') {
        return { role: 'user', text: m.content, apiText: m.apiContent || m.content };
      }
      let payload = null;
      try {
        payload = m.payloadJson ? JSON.parse(m.payloadJson) : null;
      } catch (_) {
        payload = null;
      }
      return {
        role: 'agent',
        text: (payload && payload.reply) || m.content,
        slots: payload ? payload.slots : null,
        missing: payload ? payload.missing : null,
        cards: (payload && payload.cards) || [],
        metrics: payload ? payload.metrics : null,
        plan: payload ? payload.plan : null,
        status: (payload && payload.status) || m.status,
        pending: payload ? payload.pending : null,
      };
    },

    async renameSession(s) {
      let value;
      try {
        ({ value } = await ElMessageBox.prompt('给这个会话起个名字', '重命名会话', {
          inputValue: s.title,
          confirmButtonText: '保存',
          cancelButtonText: '取消',
          inputValidator: (v) => (v && v.trim() ? true : '名称不能为空'),
        }));
      } catch (_) {
        return; // 取消
      }
      try {
        await agentApi.renameSession(s.sessionKey, value.trim());
        s.title = value.trim();
        ElMessage.success('已重命名');
      } catch (e) {
        ElMessage.error('重命名失败：' + e.message);
      }
    },

    async removeSession(s) {
      try {
        await ElMessageBox.confirm(`删除会话「${s.title}」？记录会一并清除，无法恢复。`, '删除会话', {
          type: 'warning',
          confirmButtonText: '删除',
          cancelButtonText: '取消',
        });
      } catch (_) {
        return; // 取消
      }
      try {
        await agentApi.deleteSession(s.sessionKey);
        this.sessions = this.sessions.filter((x) => x.sessionKey !== s.sessionKey);
        ElMessage.success('已删除');
        if (this.sessionKey === s.sessionKey) {
          await this.startNewSession();
        }
      } catch (e) {
        ElMessage.error('删除失败：' + e.message);
      }
    },

    /* ---------- 消息管理 ---------- */

    pushAgent(extra) {
      this.messages.push(Object.assign({ role: 'agent' }, extra));
      this.scrollToBottom();
    },

    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.stream;
        if (el) el.scrollTop = el.scrollHeight;
      });
    },

    /** 把服务端响应吸收成一条 Agent 消息，并按其指示启动轮询 */
    absorb(res) {
      if (res.sessionKey) {
        this.sessionKey = res.sessionKey;
      }
      this.pending = res.pending || null;
      this.pushAgent({
        text: res.reply,
        slots: res.slots,
        missing: res.missing,
        cards: res.cards || [],
        metrics: res.metrics,
        plan: res.plan,
        status: res.status,
      });
      const payCard = (res.cards || []).find((c) => c.type === 'PAY');
      if (payCard) {
        this.startPolling(payCard.data.orderNo);
      } else {
        this.stopPolling();
      }
      if (res.cards && res.cards.some((c) => c.type === 'RECEIPT')) {
        this.receiptShownFor = null;
      }
      // 首轮之后会话才会有标题（取首句发言），因此每轮都刷一下列表
      if (res.sessionKey) {
        this.refreshSessionList();
      }
    },

    /** 静默刷新左侧会话列表（不改变当前打开的会话） */
    async refreshSessionList() {
      try {
        const res = await agentApi.sessions();
        this.historyReady = !!(res && res.available);
        this.sessions = (res && res.sessions) || [];
      } catch (_) {
        /* 刷新失败保持原列表 */
      }
    },

    async call(payload) {
      const messages = (payload.messages || this.apiHistory());
      return agentApi.chat({
        messages,
        memberId: this.lockedMemberNo,
        pending: payload.pending !== undefined ? payload.pending : this.pending,
        sessionKey: this.sessionKey,
      });
    },

    /** 只把「用户真正说的话」交给服务端；按钮产生的展示文案不能污染语义 */
    apiHistory() {
      return this.messages
        .filter((m) => m.role === 'user' || m.text)
        .map((m) => ({
          role: m.role === 'user' ? 'user' : 'assistant',
          content: m.role === 'user' ? m.apiText || m.text : m.text,
        }))
        .filter((m) => m.content);
    },

    /* ---------- 发送 ---------- */

    async send(seedText, displayText) {
      const text = (seedText != null ? seedText : this.draft).trim();
      if (!text || this.sending) return;
      this.messages.push({
        role: 'user',
        text: displayText || text,
        apiText: text,
      });
      if (seedText == null) this.draft = '';
      this.sending = true;
      this.stopPolling();
      this.scrollToBottom();
      try {
        const res = await this.call({});
        this.absorb(res);
      } catch (e) {
        this.pushAgent({ text: '出错了：' + e.message });
      } finally {
        this.sending = false;
        this.scrollToBottom();
      }
    },

    onQuick(p) {
      this.send(p, p);
    },

    onCardAction(action) {
      if (!action) return;
      const key = action.key;
      if (key === 'open') {
        const card = action.__card;
        const path = card && card.data ? card.data.qrPath : null;
        if (path) this.$router.push(path);
        return;
      }
      if (key === 'confirm') return this.send(CONFIRM_SEED, action.label);
      if (key === 'cancel') return this.send(CANCEL_SEED, action.label);
      if (key === 'paid') return this.send(PAID_SEED, action.label);
      return this.send(action.label, action.label);
    },

    /* ---------- 轮询：手机上付款后电脑端自动到账 ---------- */

    startPolling(orderNo) {
      if (!orderNo) return;
      this.stopPolling();
      this.pollCount = 0;
      this.pollTimer = setInterval(async () => {
        this.pollCount += 1;
        if (this.pollCount > 100) {
          this.stopPolling();
          return;
        }
        try {
          const st = await agentApi.orderStatus(orderNo);
          if (st && st.status === 'PAID' && this.receiptShownFor !== orderNo) {
            this.receiptShownFor = orderNo;
            this.stopPolling();
            this.pending = null;
            this.messages.push({
              role: 'agent',
              text: '收到付款了！充值 ¥' + st.amount + ' 已入账，当前余额 ¥' + st.balance + '。',
              cards: [{
                type: 'RECEIPT',
                title: '充值成功',
                subtitle: '订单号 ' + orderNo,
                data: { orderNo, amount: st.amount, balance: st.balance },
                actions: [],
              }],
            });
            this.scrollToBottom();
          }
        } catch (_) {
          /* 轮询失败静默重试 */
        }
      }, 3000);
    },

    stopPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },

    /* ---------- 二维码 ---------- */

    /** 把还没渲染过的二维码容器补上（用 data-qr 记录内容，避免重复渲染） */
    renderQrs() {
      if (typeof QRCode === 'undefined') return;
      const nodes = this.$el.querySelectorAll('[data-qr]');
      nodes.forEach((el) => {
        const content = el.getAttribute('data-qr');
        if (!content || el.dataset.rendered === '1') return;
        el.dataset.rendered = '1';
        try {
          new QRCode(el, { text: content, width: 148, height: 148, correctLevel: QRCode.CorrectLevel.M });
        } catch (_) {
          el.textContent = content;
        }
      });
    },

    qrContent(card) {
      const path = card && card.data ? card.data.qrPath : null;
      return path ? absoluteUrl(path) : '';
    },

    /* ---------- 设置 ---------- */

    saveSettings() {
      setLlmSettings(this.settings);
      setLanBase(this.settings.lanBase);
      this.settingsVisible = false;
      ElMessage.success('已保存到本机浏览器');
      this.modelLabel = this.settings.model || this.modelLabel;
    },

    resetSettings() {
      clearLlmSettings();
      setLanBase('');
      this.settings = { baseUrl: '', apiKey: '', model: '', lanBase: '' };
      ElMessage.success('已清除本机配置');
    },

    fillLanFromLocation() {
      this.settings.lanBase = window.location.origin;
    },

    /* ---------- 展示工具 ---------- */

    fmt(v, digits = 0) {
      if (v == null) return '-';
      return digits ? Number(v).toFixed(digits) : String(Math.round(Number(v)));
    },

    metricCards(m) {
      if (!m) return [];
      const estimated = m.bodyFatSource !== 'MEASURED';
      return [
        { k: 'BMI', v: this.fmt(m.bmi, 1), s: m.bmiCategory, hl: true },
        { k: '体脂率', v: this.fmt(m.bodyFat, 1), unit: '%', s: m.bodyFatCategory, tag: estimated ? '估算' : '实测', estimated },
        { k: '基础代谢', v: this.fmt(m.bmr), unit: 'kcal', s: 'Mifflin-St Jeor' },
        { k: '每日消耗', v: this.fmt(m.tdee), unit: 'kcal', s: '活动系数 ' + m.activityFactor },
        { k: '目标摄入', v: this.fmt(m.targetCalories), unit: 'kcal', s: m.calorieFloorApplied ? '已触发安全下限' : m.goalLabel, hl: true, warn: m.calorieFloorApplied },
        { k: '蛋白质目标', v: this.fmt(m.proteinG), unit: 'g/日', s: '按目标与体重折算' },
      ];
    },

    slotEntries(slots) {
      return Object.entries(slots || {});
    },
  },
  template: `
    <div class="chat-page" :class="{ compact }">
      <div class="page-head" v-if="!compact">
        <div>
          <h1>智能助手</h1>
          <p>不用点菜单，直接说要干什么 —— 查账、充值、生成健身计划、问健身知识都能办</p>
        </div>
        <div class="page-head-actions">
          <span class="chip" :class="llmReady ? 'success' : 'warn'">
            {{ llmReady ? (settings.apiKey ? '大模型已配置（本机）' : '服务端已配大模型') : '未配置密钥 · 规则引擎兜底' }}
          </span>
          <el-button @click="settingsVisible = true">
            <app-icon name="shield" :size="15" /> API 设置
          </el-button>
        </div>
      </div>

      <div class="chat-layout">
        <!-- 会话侧栏：只有登录后（非内嵌）展示，避免会员中心里挤成两栏 -->
        <aside class="chat-sessions" v-if="!compact">
          <div class="sess-head">
            <span class="sess-head-title"><app-icon name="course" :size="15" /> 会话记录</span>
            <button class="sess-new" :disabled="sending" @click="startNewSession">
              <app-icon name="plus" :size="13" /> 新会话
            </button>
          </div>
          <div class="sess-list" v-loading="sessionsLoading">
            <div v-for="s in sessions" :key="s.sessionKey" class="sess-item"
              :class="{ active: s.sessionKey === sessionKey }" @click="openSession(s.sessionKey)">
              <div class="sess-main">
                <div class="sess-title">{{ s.title || '新会话' }}</div>
                <div class="sess-preview">{{ s.preview || '（暂无内容）' }}</div>
                <div class="sess-meta">
                  <span>{{ s.messageCount || 0 }} 条</span>
                  <span v-if="s.updatedAt">· {{ s.updatedAt.slice(5, 16) }}</span>
                </div>
              </div>
              <div class="sess-tools">
                <button class="sess-act" title="重命名" @click.stop="renameSession(s)">
                  <app-icon name="edit" :size="13" />
                </button>
                <button class="sess-act" title="删除" @click.stop="removeSession(s)">
                  <app-icon name="trash" :size="13" />
                </button>
              </div>
            </div>
            <div class="sess-empty" v-if="!sessionsLoading && !sessions.length">
              还没有会话记录。说第一句话时会自动开始保存。
            </div>
            <div class="sess-warn" v-if="!historyReady">
              历史记录服务未就绪，本次对话不会被保存（对话功能不受影响）。
            </div>
          </div>
        </aside>

        <div class="chat-shell" :class="{ 'is-compact': compact }">
        <div class="chat-stream" ref="stream">
          <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role">
            <div class="msg-avatar">
              <app-icon :name="m.role === 'user' ? 'user' : 'sparkles'" :size="16" />
            </div>
            <div class="msg-body">
              <div class="bubble" v-if="m.text">{{ m.text }}</div>

              <div class="slot-chips" v-if="slotEntries(m.slots).length">
                <span v-for="([k, v]) in slotEntries(m.slots)" :key="k" class="slot-chip">
                  {{ k }} <b>{{ v }}</b>
                </span>
              </div>

              <div class="missing-hint" v-if="m.missing && m.missing.length">
                还差：{{ m.missing.join('、') }}
              </div>

              <!-- 卡片区 -->
              <template v-for="(card, ci) in (m.cards || [])" :key="ci">
                <!-- 能力清单 -->
                <div class="card-box notice" v-if="card.type === 'NOTICE'">
                  <div class="card-box-head">{{ card.title }}</div>
                  <ul class="notice-list">
                    <li v-for="(it, ii) in card.data.items" :key="ii">{{ it }}</li>
                  </ul>
                </div>

                <!-- 来源卡：知识问答的引用出处。摆出来，用户才知道这不是模型随口说的 -->
                <div class="card-box source" v-else-if="card.type === 'SOURCE'">
                  <div class="card-box-head">
                    {{ card.title }}
                    <span class="card-box-sub" v-if="card.subtitle">{{ card.subtitle }}</span>
                  </div>
                  <ul class="source-list">
                    <li v-for="(it, ii) in card.data.items" :key="ii">
                      <span class="source-idx">{{ ii + 1 }}</span>{{ it }}
                    </li>
                  </ul>
                </div>

                <!-- 确认卡 -->
                <div class="card-box confirm" v-else-if="card.type === 'CONFIRM'">
                  <div class="card-box-head">{{ card.title }}</div>
                  <div class="confirm-amount">¥{{ card.data.amount }}</div>
                  <div class="confirm-meta">当前余额 ¥{{ card.data.balance }}</div>
                  <div class="card-actions">
                    <button v-for="a in card.actions" :key="a.key"
                      class="btn" :class="a.style === 'primary' ? 'btn-primary' : 'btn-plain'"
                      @click="onCardAction(Object.assign({ __card: card }, a))">{{ a.label }}</button>
                  </div>
                </div>

                <!-- 支付卡 -->
                <div class="card-box pay" v-else-if="card.type === 'PAY'">
                  <div class="card-box-head">{{ card.title }}</div>
                  <div class="pay-body">
                    <div class="qr-holder" :data-qr="qrContent(card)"></div>
                    <div class="pay-side">
                      <div class="pay-amount-sm">¥{{ card.data.amount }}</div>
                      <div class="pay-meta-sm">订单号 {{ card.data.orderNo }}</div>
                      <div class="pay-tip">用手机扫码打开支付页；付款后这里会自动到账。</div>
                      <div class="pay-link" v-if="lanIsLocalhost">
                        当前地址是 localhost，手机扫不开 —— 请在「API 设置」里填写本机局域网地址。
                      </div>
                    </div>
                  </div>
                  <div class="card-actions">
                    <button v-for="a in card.actions" :key="a.key"
                      class="btn" :class="a.style === 'primary' ? 'btn-primary' : 'btn-plain'"
                      @click="onCardAction(Object.assign({ __card: card }, a))">{{ a.label }}</button>
                  </div>
                </div>

                <!-- 列表卡 -->
                <div class="card-box list" v-else-if="card.type === 'LIST'">
                  <div class="card-box-head">
                    {{ card.title }}
                    <span class="card-box-sub" v-if="card.subtitle">{{ card.subtitle }}</span>
                  </div>
                  <table class="mini-table" v-if="card.data.rows && card.data.rows.length">
                    <thead>
                      <tr><th v-for="(c, i2) in card.data.columns" :key="i2">{{ c }}</th></tr>
                    </thead>
                    <tbody>
                      <tr v-for="(row, ri) in card.data.rows" :key="ri">
                        <td v-for="(cell, ci2) in row" :key="ci2">{{ cell }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <div class="mini-empty" v-else>{{ card.data.empty || '暂无数据' }}</div>
                </div>

                <!-- 回执卡 -->
                <div class="card-box receipt" v-else-if="card.type === 'RECEIPT'">
                  <div class="receipt-row">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#10b981" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.5 2.5L16 9.5"/>
                    </svg>
                    <div>
                      <div class="receipt-t">{{ card.title }} · ¥{{ card.data.amount }}</div>
                      <div class="receipt-d">订单 {{ card.data.orderNo }} · 当前余额 ¥{{ card.data.balance }}</div>
                    </div>
                  </div>
                </div>
              </template>

              <!-- 计划结果（沿用证据 + 三栏结构） -->
              <template v-if="m.plan">
                <div class="card-box result">
                  <div class="card-box-head">
                    计算证据
                    <span class="card-box-sub">全部可手工复算，不经过大模型</span>
                  </div>
                  <div class="metric-grid tight">
                    <div v-for="c in metricCards(m.metrics)" :key="c.k" class="metric" :class="{ hl: c.hl }">
                      <div class="metric-k">
                        {{ c.k }}
                        <span v-if="c.tag" class="src-tag" :class="{ measured: !c.estimated }">{{ c.tag }}</span>
                      </div>
                      <div class="metric-v">{{ c.v }}<small v-if="c.unit">{{ c.unit }}</small></div>
                      <div class="metric-s">{{ c.s }}</div>
                    </div>
                  </div>
                </div>

                <div class="card-box result">
                  <div class="card-box-head">
                    健身计划
                    <span class="card-box-sub">
                      {{ m.metrics.genderLabel }} · {{ m.metrics.age }} 岁 · 目标 {{ m.metrics.goalLabel }}
                    </span>
                  </div>
                  <div class="summary-card" v-if="m.plan.summary">
                    <div class="t">总体评估</div>
                    <div class="c">{{ m.plan.summary }}</div>
                  </div>
                  <div class="plan-cols">
                    <div class="plan-col" v-if="m.plan.training && m.plan.training.length">
                      <div class="plan-col-head t1"><app-icon name="dumbbell" :size="16" /> 训练安排</div>
                      <ul><li v-for="(t, i3) in m.plan.training" :key="i3">{{ t }}</li></ul>
                    </div>
                    <div class="plan-col" v-if="m.plan.diet && m.plan.diet.length">
                      <div class="plan-col-head t2"><app-icon name="flame" :size="16" /> 饮食建议</div>
                      <ul><li v-for="(t, i4) in m.plan.diet" :key="i4">{{ t }}</li></ul>
                    </div>
                    <div class="plan-col" v-if="m.plan.precautions && m.plan.precautions.length">
                      <div class="plan-col-head t3"><app-icon name="shield" :size="16" /> 注意事项</div>
                      <ul><li v-for="(t, i5) in m.plan.precautions" :key="i5">{{ t }}</li></ul>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>

          <div v-if="sending" class="msg agent">
            <div class="msg-avatar"><app-icon name="sparkles" :size="16" /></div>
            <div class="msg-body"><div class="bubble typing"><i></i><i></i><i></i></div></div>
          </div>
        </div>

        <div class="chat-input">
          <div class="quick-row">
            <button v-for="p in quickPrompts" :key="p" class="quick-chip" :disabled="sending"
              @click="onQuick(p)">{{ p }}</button>
          </div>
          <div class="input-row">
            <textarea v-model="draft" rows="1" class="chat-textarea"
              placeholder="说点什么，比如「我要充值 200」「我卡里还有多少钱」"
              @keydown.enter.exact.prevent="send()"></textarea>
            <button class="btn btn-primary send-btn" :disabled="sending || !draft.trim()" @click="send()">
              {{ sending ? '…' : '发送' }}
            </button>
          </div>
          <div class="input-hint">Enter 发送 · Shift + Enter 换行</div>
        </div>
        </div>
      </div>

      <el-dialog v-model="settingsVisible" title="连接设置" width="560px">
        <el-alert type="info" :closable="false" show-icon
          :title="'只保存在本机浏览器里，随请求头发送，服务端不保存密钥'" style="margin-bottom:16px" />
        <el-form label-position="top">
          <el-form-item label="大模型 API 地址（Base URL）">
            <el-input v-model="settings.baseUrl" placeholder="https://api.deepseek.com" clearable />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="settings.apiKey" type="password" show-password
              placeholder="留空则使用服务端配置；服务端也没配就走规则引擎" clearable />
          </el-form-item>
          <el-form-item label="模型名">
            <el-select v-model="settings.model" style="width:100%" filterable allow-create
              default-first-option placeholder="deepseek-flash">
              <el-option value="deepseek-flash" label="deepseek-flash（快、便宜）" />
              <el-option value="deepseek-v4-pro" label="deepseek-v4-pro（更强）" />
            </el-select>
          </el-form-item>
          <el-form-item label="本机局域网地址（手机扫码用）">
            <el-input v-model="settings.lanBase" placeholder="http://192.168.0.104:8088" clearable />
            <div class="form-hint">
              手机要和电脑在同一个 WiFi。二维码内容会拼成
              [这个地址] + /pay/订单号。<br/>
              当前默认：<code>{{ lanBase }}</code>
              <span v-if="lanIsLocalhost">（localhost 手机扫不开，建议填写局域网 IP）</span>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="fillLanFromLocation">用当前地址填充</el-button>
          <el-button @click="resetSettings">清除本机配置</el-button>
          <el-button type="primary" @click="saveSettings">保存</el-button>
        </template>
      </el-dialog>
    </div>
  `,
};
