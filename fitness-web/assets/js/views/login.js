/**
 * 登录页。管理员与会员共用同一入口，通过身份切换区分。
 * 两类登录分别对应业务系统的 /getAdminPassword 与 /getMemberPassword。
 */
import { gymApi } from '../api.js';
import { setAdminSession, setMemberSession } from '../auth.js';

const { ElMessage } = ElementPlus;

export default {
  name: 'LoginPage',
  data() {
    return {
      identity: 'admin', // admin | member
      account: '',
      password: '',
      loading: false,
      error: '',
    };
  },
  computed: {
    isAdmin() {
      return this.identity === 'admin';
    },
    accountLabel() {
      return this.isAdmin ? '管理员账号' : '手机号';
    },
    accountPlaceholder() {
      return this.isAdmin ? '请输入管理员账号' : '请输入注册手机号';
    },
  },
  methods: {
    switchIdentity(v) {
      this.identity = v;
      this.error = '';
    },
    async submit() {
      if (!this.account || !this.password) {
        ElMessage.warning('请输入账号与密码');
        return;
      }
      this.loading = true;
      this.error = '';
      try {
        if (this.isAdmin) {
          const res = await gymApi.adminLogin(this.account, this.password);
          if (res && res.code === 200) {
            setAdminSession(res);
            ElMessage.success('登录成功');
            this.$router.replace('/dashboard');
          } else {
            this.error = (res && res.message) || '账号或密码错误';
          }
        } else {
          const res = await gymApi.memberLogin(this.account, this.password);
          if (res && res.code === 200) {
            setMemberSession(res);
            ElMessage.success('登录成功');
            this.$router.replace('/me/agent');
          } else {
            this.error = (res && res.message) || '账号或密码错误';
          }
        }
      } catch (e) {
        this.error = `登录请求失败：${e.message}`;
      } finally {
        this.loading = false;
      }
    },
  },
  template: `
    <div class="login-page">
      <div class="login-art">
        <div class="login-art-inner">
          <div class="login-brand">
            <div class="login-brand-mark">
              <svg viewBox="0 0 32 32" width="24" height="24">
                <path d="M9 12v8M23 12v8M6 14v4M26 14v4M9 16h14"
                  stroke="#fff" stroke-width="2.4" stroke-linecap="round" fill="none"/>
              </svg>
            </div>
            <div>
              <div class="login-brand-name">健身会员智慧管理系统</div>
              <div class="login-brand-sub">SMART FITNESS PLATFORM</div>
            </div>
          </div>

          <h1 class="login-title">让每一份计划<br/><em>都有数据支撑</em></h1>
          <p class="login-desc">
            会员档案、课程报名、器材台账与经营看板一体化管理，
            并内置 AI 智能问答：依据身高、体重、体脂率与目标生成科学健身计划。
          </p>

          <div class="login-points">
            <div class="login-point">
              <div class="login-point-ico">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round">
                  <circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/>
                </svg>
              </div>
              <div>
                <div class="login-point-t">证据驱动的智能问答</div>
                <div class="login-point-d">BMI / 基础代谢 / 目标热量全部公式计算，大模型只负责组织语言</div>
              </div>
            </div>
            <div class="login-point">
              <div class="login-point-ico">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/>
                </svg>
              </div>
              <div>
                <div class="login-point-t">永不空手而归</div>
                <div class="login-point-d">大模型不可用时自动降级为规则引擎，服务始终可用</div>
              </div>
            </div>
            <div class="login-point">
              <div class="login-point-ico">
                <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round">
                  <path d="M3 3v18h18"/><path d="M7 15l3.5-4 3 2.5L20 7"/>
                </svg>
              </div>
              <div>
                <div class="login-point-t">经营数据一目了然</div>
                <div class="login-point-d">开卡趋势、性别分布、会员规模实时呈现</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="login-form-wrap">
        <div class="login-form">
          <h2>欢迎回来</h2>
          <p class="sub">请选择身份并登录系统</p>

          <div class="identity-switch">
            <button :class="{ on: isAdmin }" @click="switchIdentity('admin')">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
              </svg>
              管理员
            </button>
            <button :class="{ on: !isAdmin }" @click="switchIdentity('member')">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
                <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
              </svg>
              会员
            </button>
          </div>

          <el-form label-position="top" @submit.prevent>
            <el-form-item :label="accountLabel">
              <el-input v-model="account" :placeholder="accountPlaceholder" size="large"
                clearable @keyup.enter="submit" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="password" type="password" placeholder="请输入密码" size="large"
                show-password @keyup.enter="submit" />
            </el-form-item>
          </el-form>

          <el-alert v-if="error" type="error" :closable="false" show-icon :title="error"
            style="margin-bottom:16px" />

          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="submit">
            {{ loading ? '登录中…' : '登 录' }}
          </el-button>

          <div class="login-hint">
            演示账号　管理员 <code>admin / admin</code>　·　会员 <code>18470558967 / admin</code>
          </div>
        </div>
      </div>
    </div>
  `,
};
