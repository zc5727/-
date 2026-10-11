/**
 * 会员中心：会员登录后可见。
 * 包含个人资料、我的课程、充值记录，以及针对本人的智能健身计划。
 */
import { gymApi } from '../api.js';
import { session, initials } from '../auth.js';
import AgentPage from './agent.js';

const { ElMessage } = ElementPlus;

const dateText = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-');

export default {
  name: 'MemberPortalPage',
  components: { AgentPage },
  data() {
    return {
      session,
      tab: 'profile',
      profile: null,
      registers: [],
      recharges: [],
      loading: false,
    };
  },
  computed: {
    memberNo() {
      return session.memberNo;
    },
    avatarText() {
      return initials(this.profile ? this.profile.memberName : session.name);
    },
    rechargeTotal() {
      return this.recharges
        .filter((r) => Number(r.rechargeStatus) === 1)
        .reduce((s, r) => s + Number(r.rechargeMoney || 0), 0);
    },
  },
  mounted() {
    this.loadAll();
  },
  methods: {
    dateText,
    async loadAll() {
      if (!this.memberNo) {
        ElMessage.warning('当前登录态缺少会员编号，请重新登录');
        return;
      }
      this.loading = true;
      try {
        const [profile, registers, recharges] = await Promise.all([
          gymApi.memberById(this.memberNo),
          gymApi.registerByMember(this.memberNo),
          gymApi.rechargeByMember(this.memberNo),
        ]);
        this.profile = profile || null;
        this.registers = Array.isArray(registers) ? registers : [];
        this.recharges = Array.isArray(recharges) ? recharges : [];
      } catch (e) {
        ElMessage.error(`加载个人数据失败：${e.message}`);
      } finally {
        this.loading = false;
      }
    },
  },
  template: `
    <div v-loading="loading">
      <div class="profile-hero">
        <div class="profile-avatar">{{ avatarText }}</div>
        <div>
          <div class="profile-name">{{ (profile && profile.memberName) || session.name || '会员' }}</div>
          <div class="profile-meta">
            <span>编号 {{ memberNo }}</span>
            <span>{{ (profile && profile.memberPhone) || session.account }}</span>
            <span v-if="profile && profile.memberGender">性别 {{ profile.memberGender === 'M' ? '男' : '女' }}</span>
            <span v-if="profile && profile.memberAge">年龄 {{ profile.memberAge }} 岁</span>
            <span v-if="profile && profile.memberHeight">身高 {{ profile.memberHeight }} cm</span>
            <span v-if="profile && profile.memberWeight">体重 {{ profile.memberWeight }} kg</span>
          </div>
        </div>
        <div class="profile-stats">
          <div>
            <div class="profile-stat-v">¥{{ profile && profile.memberChange != null ? profile.memberChange : 0 }}</div>
            <div class="profile-stat-k">账户余额</div>
          </div>
          <div>
            <div class="profile-stat-v">{{ profile && profile.memberIntegral != null ? profile.memberIntegral : 0 }}</div>
            <div class="profile-stat-k">会员积分</div>
          </div>
          <div>
            <div class="profile-stat-v">¥{{ rechargeTotal.toFixed(2) }}</div>
            <div class="profile-stat-k">累计充值</div>
          </div>
        </div>
      </div>

      <div class="card mt-18">
        <div class="toolbar">
          <el-radio-group v-model="tab">
            <el-radio-button value="profile">个人信息</el-radio-button>
            <el-radio-button value="courses">我的课程</el-radio-button>
            <el-radio-button value="recharge">充值记录</el-radio-button>
            <el-radio-button value="plan">我的健身计划</el-radio-button>
          </el-radio-group>
          <div class="spacer"></div>
          <el-button @click="loadAll"><app-icon name="refresh" :size="15" /> 刷新</el-button>
        </div>

        <div class="card-body" v-show="tab === 'profile'">
          <div class="metric-grid">
            <div class="metric">
              <div class="metric-k">会员卡等级</div>
              <div class="metric-v">{{ profile && profile.cardClass != null ? profile.cardClass : '-' }}</div>
              <div class="metric-s">下一等级需 {{ profile && profile.cardNextClass != null ? profile.cardNextClass : '-' }}</div>
            </div>
            <div class="metric">
              <div class="metric-k">权限等级</div>
              <div class="metric-v">{{ profile && profile.memberPower ? profile.memberPower : '-' }}</div>
              <div class="metric-s">由管理员配置</div>
            </div>
            <div class="metric">
              <div class="metric-k">开卡时间</div>
              <div class="metric-v" style="font-size:15px">{{ profile ? dateText(profile.cardTime) : '-' }}</div>
              <div class="metric-s">会员起始日期</div>
            </div>
            <div class="metric hl">
              <div class="metric-k">个性签名</div>
              <div class="metric-v" style="font-size:14px;font-weight:600">
                {{ (profile && profile.personalizedSignature) || '还没有签名' }}
              </div>
              <div class="metric-s">来自会员档案</div>
            </div>
          </div>
        </div>

        <div v-show="tab === 'courses'">
          <el-table :data="registers" style="width:100%" empty-text="还没有报名任何课程">
            <el-table-column prop="courseName" label="课程名称" min-width="150" />
            <el-table-column prop="courseDuration" label="课时" width="100">
              <template #default="scope">{{ scope.row.courseDuration }} 节</template>
            </el-table-column>
            <el-table-column prop="employeeName" label="授课教练" min-width="120" />
            <el-table-column prop="employeePhone" label="教练电话" min-width="140" />
            <el-table-column prop="courseTime" label="开课日期" min-width="130" />
          </el-table>
        </div>

        <div v-show="tab === 'recharge'">
          <el-table :data="recharges" style="width:100%" empty-text="还没有充值记录">
            <el-table-column prop="rechargeNo" label="流水号" width="100" />
            <el-table-column prop="rechargeDate" label="充值日期" min-width="140" />
            <el-table-column prop="rechargeMethod" label="方式" min-width="120">
              <template #default="scope"><span class="chip brand">{{ scope.row.rechargeMethod }}</span></template>
            </el-table-column>
            <el-table-column prop="rechargeMoney" label="金额" min-width="120" align="right">
              <template #default="scope">
                <span style="font-weight:700;color:#4f46e5">¥{{ Number(scope.row.rechargeMoney).toFixed(2) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="rechargeStatus" label="状态" width="110">
              <template #default="scope">
                <span class="chip" :class="Number(scope.row.rechargeStatus) === 1 ? 'success' : 'warn'">
                  {{ Number(scope.row.rechargeStatus) === 1 ? '成功' : '未完成' }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div v-if="tab === 'plan'" class="mt-18">
        <agent-page :locked-member-no="memberNo" compact />
      </div>
    </div>
  `,
};
