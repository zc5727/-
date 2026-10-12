/**
 * 应用入口：外壳布局 + 路由 + 挂载。
 *
 * 布局策略：登录页独占整屏；进入系统后统一使用「侧边栏 + 顶栏 + 内容区」外壳，
 * 菜单项按当前角色（管理员 / 会员）动态生成，避免会员看到管理端入口。
 */
import { session, clearSession, initials, isLoggedIn } from './auth.js';
import { AppIcon } from './icons.js';

import LoginPage from './views/login.js';
import DashboardPage from './views/dashboard.js';
import AgentPage from './views/agent.js';
import PayPage from './views/pay.js';
import RechargePage from './views/recharge.js';
import MemberPortalPage from './views/member.js';
import KnowledgePage from './views/knowledge.js';
import { MemberPage, CoursePage, EquipmentPage, EmployeePage, RegisterPage } from './views/resources.js';
import ObservabilityPage from './views/observability.js';
import OpsPage from './views/ops.js';
import TrainingPage from './views/training.js';

const { createApp, computed } = Vue;
const { createRouter, createWebHistory } = VueRouter;

/* ============================================================
   菜单定义
   ============================================================ */
const ADMIN_MENU = [
  { group: '总览' },
  { path: '/dashboard', label: '数据看板', icon: 'dashboard' },
  { path: '/agent', label: '智能问答', icon: 'sparkles', badge: 'AI' },
  { path: '/knowledge', label: '知识库', icon: 'course' },
  { path: '/observability', label: '运行观测', icon: 'activity' },
  { group: '会员运营' },
  { path: '/ops', label: '智能运营中枢', icon: 'target' },
  { path: '/members', label: '会员管理', icon: 'users' },
  { path: '/registers', label: '课程报名', icon: 'clipboard' },
  { path: '/recharge', label: '充值管理', icon: 'wallet' },
  { group: '资源管理' },
  { path: '/courses', label: '课程管理', icon: 'course' },
  { path: '/equipment', label: '器材管理', icon: 'box' },
  { path: '/employees', label: '员工管理', icon: 'badge' },
];

const MEMBER_MENU = [
  { group: '我的' },
  { path: '/me/agent', label: '智能助手', icon: 'sparkles', badge: 'AI' },
  { path: '/training', label: '我的训练', icon: 'target' },
  { path: '/me', label: '会员中心', icon: 'user' },
];

/* 会员端的对话页：把登录态里的会员编号锁给智能体，
   于是"查余额""充值"这些动作天然只能作用于本人，不需要额外传参。 */
const MemberAgentPage = {
  name: 'MemberAgentPage',
  components: { AgentPage },
  data() {
    return { session };
  },
  template: `<agent-page :locked-member-no="session.memberNo" />`,
};

/* ============================================================
   外壳布局
   ============================================================ */
const Shell = {
  name: 'AppShell',
  components: { AppIcon },
  data() {
    return { session };
  },
  computed: {
    menu() {
      return session.role === 'member' ? MEMBER_MENU : ADMIN_MENU;
    },
    avatarText() {
      return initials(session.name);
    },
    roleLabel() {
      return session.role === 'member' ? '会员' : '管理员';
    },
    pageTitle() {
      const item = this.menu.find((m) => m.path && m.path === this.$route.path);
      return item ? item.label : '健身会员智慧管理系统';
    },
    llmNote() {
      return session.role === 'member' ? '会员中心' : '管理后台';
    },
  },
  methods: {
    logout() {
      clearSession();
      this.$message ? this.$message.success('已退出登录') : null;
      this.$router.replace('/login');
    },
  },
  template: `
    <div class="shell">
      <aside class="sidebar">
        <div class="side-brand">
          <div class="side-brand-mark">
            <svg viewBox="0 0 32 32" width="19" height="19">
              <path d="M9 12v8M23 12v8M6 14v4M26 14v4M9 16h14"
                stroke="#fff" stroke-width="2.4" stroke-linecap="round" fill="none"/>
            </svg>
          </div>
          <div>
            <div class="side-brand-name">健身智慧管理平台</div>
            <div class="side-brand-sub">FITNESS PLATFORM</div>
          </div>
        </div>

        <div class="side-scroll">
          <template v-for="(m, i) in menu" :key="i">
            <div v-if="m.group" class="side-group-title">{{ m.group }}</div>
            <router-link v-else :to="m.path" class="side-link">
              <span class="side-ico"><app-icon :name="m.icon" :size="17" /></span>
              <span>{{ m.label }}</span>
              <span v-if="m.badge" class="badge-new">{{ m.badge }}</span>
            </router-link>
          </template>
        </div>

        <div class="side-foot">
          <div class="side-status">
            <span class="dot"></span>
            <span>智能体服务运行中</span>
          </div>
          <div>{{ llmNote }} · v1.0</div>
        </div>
      </aside>

      <div class="main">
        <header class="topbar">
          <div>
            <div class="topbar-title">{{ pageTitle }}</div>
          </div>
          <div class="topbar-spacer"></div>
          <div class="topbar-user" @click="logout" title="点击退出登录">
            <div class="avatar">{{ avatarText }}</div>
            <div>
              <div class="topbar-user-name">{{ session.name || '用户' }}</div>
              <div class="topbar-user-role">{{ roleLabel }} · 退出</div>
            </div>
          </div>
        </header>

        <main class="content">
          <router-view />
        </main>
      </div>
    </div>
  `,
};

/* ============================================================
   路由
   ============================================================ */
const routes = [
  { path: '/login', component: LoginPage, meta: { public: true } },
  /* 支付页：手机扫码打开时并没有登录态，因此必须公开，且不套后台外壳 */
  { path: '/pay/:orderNo', component: PayPage, meta: { public: true } },
  { path: '/', redirect: () => (session.role === 'member' ? '/me/agent' : '/dashboard') },
  { path: '/dashboard', component: DashboardPage, meta: { role: 'admin' } },
  { path: '/agent', component: AgentPage, meta: { role: 'admin' } },
  { path: '/knowledge', component: KnowledgePage, meta: { role: 'admin' } },
  { path: '/observability', component: ObservabilityPage, meta: { role: 'admin' } },
  { path: '/ops', component: OpsPage, meta: { role: 'admin' } },
  { path: '/members', component: MemberPage, meta: { role: 'admin' } },
  { path: '/courses', component: CoursePage, meta: { role: 'admin' } },
  { path: '/equipment', component: EquipmentPage, meta: { role: 'admin' } },
  { path: '/employees', component: EmployeePage, meta: { role: 'admin' } },
  { path: '/registers', component: RegisterPage, meta: { role: 'admin' } },
  { path: '/recharge', component: RechargePage, meta: { role: 'admin' } },
  { path: '/me/agent', component: MemberAgentPage, meta: { role: 'member' } },
  { path: '/training', component: TrainingPage, meta: { role: 'member' } },
  { path: '/me', component: MemberPortalPage, meta: { role: 'member' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
});

router.beforeEach((to) => {
  if (to.meta.public) {
    // 只有登录页需要"已登录就别再进"；支付页必须放行，
    // 否则在电脑上点开支付页会被这里的重定向顶回后台。
    if (to.path === '/login' && isLoggedIn()) {
      return session.role === 'member' ? '/me/agent' : '/dashboard';
    }
    return true;
  }
  if (!isLoggedIn()) {
    return '/login';
  }
  if (to.meta.role && to.meta.role !== session.role) {
    return session.role === 'member' ? '/me/agent' : '/dashboard';
  }
  return true;
});

/* ============================================================
   根组件
   ============================================================ */
const Root = {
  components: { Shell },
  computed: {
    showShell() {
      const p = this.$route.path;
      // 支付页整页独立（手机扫码进来时没有登录态，也不该套后台边框）
      return isLoggedIn() && p !== '/login' && !p.startsWith('/pay/');
    },
  },
  template: `<shell v-if="showShell" /><router-view v-else />`,
};

const app = createApp(Root);

app.component('app-icon', AppIcon);
app.use(ElementPlus, { locale: window.ElementPlusLocaleZhCn });
app.use(router);
app.mount('#app');
