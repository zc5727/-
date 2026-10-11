/**
 * 支付页 —— 手机扫码打开的那一页。
 *
 * 这一页有两个刻意为之的设计：
 *   1. **反电诈提示放在最上面、字号最大**。扫码支付是诈骗高发场景，
 *      先把"凡是要求转账到安全账户的都是诈骗"怼到眼前，再谈付款。
 *   2. **独立于登录态**：手机扫码时并没有登录，所以本页是公开路由，
 *      也不套用后台外壳，整页为移动端窄屏设计。
 */
import { agentApi } from '../api.js';

export default {
  name: 'PayPage',
  data() {
    return {
      loading: true,
      error: '',
      order: null,
      paying: false,
      paid: false,
      paidBalance: null,
    };
  },
  computed: {
    orderNo() {
      return this.$route.params.orderNo;
    },
    amount() {
      return this.order && this.order.amount != null ? this.order.amount : '-';
    },
  },
  mounted() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = '';
      try {
        const res = await agentApi.orderStatus(this.orderNo);
        this.order = res || null;
        if (this.order && this.order.status === 'PAID') {
          this.paid = true;
          this.paidBalance = this.order.balance;
        }
      } catch (e) {
        this.error = e.message;
      } finally {
        this.loading = false;
      }
    },
    async pay() {
      this.paying = true;
      this.error = '';
      try {
        const res = await agentApi.confirmPay(this.orderNo);
        this.paidBalance = res && res.balance != null ? res.balance : null;
        this.paid = true;
      } catch (e) {
        this.error = e.message;
      } finally {
        this.paying = false;
      }
    },
  },
  template: `
    <div class="pay-page">
      <!-- 反电诈警示：放最上面，字号最大 -->
      <div class="fraud-banner">
        <div class="fraud-mark">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#fff" stroke-width="1.9" stroke-linecap="round">
            <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            <path d="M12 8v5M12 16.5v.5"/>
          </svg>
        </div>
        <div class="fraud-title">警惕电信网络诈骗</div>
        <div class="fraud-main">凡是要求你转账到「安全账户」的，都是诈骗。</div>
        <ul class="fraud-list">
          <li>官方任何情况下都不会向你索要短信验证码</li>
          <li>陌生链接不要点、来路不明的二维码不要扫</li>
          <li>涉及转账先与家人核实，或拨打 <b>96110</b></li>
        </ul>
      </div>

      <div class="pay-card">
        <div v-if="loading" class="pay-loading">正在读取订单…</div>

        <div v-else-if="error" class="pay-error">
          <div class="pay-error-t">订单读取失败</div>
          <div class="pay-error-d">{{ error }}</div>
        </div>

        <template v-else-if="paid">
          <div class="pay-paid-mark">
            <svg viewBox="0 0 24 24" width="34" height="34" fill="none" stroke="#10b981" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/><path d="M8 12.5l2.5 2.5L16 9.5"/>
            </svg>
          </div>
          <div class="pay-paid-t">支付完成</div>
          <div class="pay-paid-d">
            订单 {{ orderNo }} 已入账
            <template v-if="paidBalance != null">，当前余额 ¥{{ paidBalance }}</template>
          </div>
          <div class="pay-hint-panel">
            可以回到电脑前继续对话了 —— 那边的助手会自动收到到账通知。
          </div>
        </template>

        <template v-else>
          <div class="pay-label">支付金额</div>
          <div class="pay-amount">¥{{ amount }}</div>
          <div class="pay-meta">
            <div><span>订单号</span><b>{{ orderNo }}</b></div>
            <div><span>会员编号</span><b>{{ order.memberNo }}</b></div>
            <div><span>状态</span><b>待支付</b></div>
          </div>

          <div class="pay-hint-panel">
            这是<b>演示环境</b>，不会发生真实扣款。点击下方按钮即等同于「扫码付款成功」。
          </div>

          <button class="pay-btn" :disabled="paying" @click="pay">
            {{ paying ? '处理中…' : '已知晓风险 · 模拟支付 ¥' + amount }}
          </button>
        </template>
      </div>

      <div class="pay-foot">健身会员智慧管理系统 · 演示支付页</div>
    </div>
  `,
};
