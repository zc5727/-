/**
 * 我的训练 —— 计划执行闭环的会员端入口。
 *
 * 计划生成只是"说"，这一页管的是"做"：
 *   · 列出由计划自动展开的每日任务（近 7 天 / 近 30 天可切）
 *   · 支持完成 / 跳过 / 撤销三种打卡动作
 *   · 顶部显示依从率，并在低于阈值时给出降强度提示
 *
 * 归属解析与会话一致（token 优先，其次浏览器标识），
 * 因此会员只会看到自己的任务。
 */
import { agentApi } from '../api.js';

const { ElMessage } = ElementPlus;

const TYPE_LABEL = {
  cardio: '有氧',
  strength: '力量',
  rest: '休息',
};

const STATUS_LABEL = {
  TODO: '待完成',
  DONE: '已完成',
  SKIPPED: '已跳过',
};

export default {
  name: 'TrainingPage',
  data() {
    return {
      range: 7,
      loading: false,
      items: [],
      adh: null,
    };
  },
  mounted() {
    this.$nextTick(() => this.load());
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const from = this.dateAgo(this.range - 1);
        const to = this.dateAhead(6);
        const [tasks, adh] = await Promise.all([
          agentApi.planTasks(from, to),
          agentApi.planAdherence(30),
        ]);
        this.items = (tasks && tasks.items) || [];
        this.adh = adh;
      } catch (e) {
        ElMessage.error('加载训练任务失败：' + e.message);
      } finally {
        this.loading = false;
      }
    },
    async checkIn(row, action) {
      try {
        const r = await agentApi.planCheckIn(row.id, action, '', '');
        if (r && r.ok) {
          ElMessage.success(action === 'DONE' ? '已打卡 ✅' : action === 'SKIP' ? '已跳过' : '已撤销');
          await this.load();
        } else {
          ElMessage.warning('操作失败：' + (r && r.reason || '任务不存在或不属于当前用户'));
        }
      } catch (e) {
        ElMessage.error('操作失败：' + e.message);
      }
    },
    typeLabel(v) {
      return TYPE_LABEL[v] || v || '-';
    },
    statusLabel(v) {
      return STATUS_LABEL[v] || v || '-';
    },
    dateAgo(n) {
      const d = new Date();
      d.setDate(d.getDate() - n);
      return d.toISOString().slice(0, 10);
    },
    dateAhead(n) {
      const d = new Date();
      d.setDate(d.getDate() + n);
      return d.toISOString().slice(0, 10);
    },
  },
  template: `
<div class="page-wrap">
  <div class="page-head">
    <div>
      <h2>我的训练</h2>
      <p class="muted">由健身计划自动展开的每日任务，支持打卡与跳过。</p>
    </div>
    <div class="toolbar">
      <el-select v-model="range" size="small" style="width:120px" @change="load">
        <el-option :value="7" label="近 7 天" />
        <el-option :value="30" label="近 30 天" />
      </el-select>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
    </div>
  </div>

  <div v-if="adh && adh.available && adh.done + adh.skipped + adh.overdue > 0" class="adh-box">
    <div class="adh-rate" :class="{ low: adh.low }">{{ adh.rate }}%</div>
    <div class="adh-meta">
      <div class="adh-title">近 {{ adh.days }} 天依从率</div>
      <div class="muted">完成 {{ adh.done }} · 跳过 {{ adh.skipped }} · 逾期 {{ adh.overdue }}</div>
      <div v-if="adh.low" class="adh-tip">
        依从率偏低，下次生成计划时会自动降低训练强度——跟不上的计划等于没有计划。
      </div>
    </div>
  </div>

  <div v-if="!items.length" class="empty-box">
    暂无训练任务。先去智能助手生成一份健身计划，系统会自动把它展开成每日任务。
  </div>

  <div v-else class="card">
    <div class="card-h">任务清单（{{ items.length }} 条）</div>
    <el-table :data="items" size="small" border stripe max-height="520">
      <el-table-column prop="plannedDate" label="日期" width="120">
        <template #default="{ row }">{{ fmtDate(row.plannedDate) }}</template>
      </el-table-column>
      <el-table-column prop="dayIndex" label="第几天" width="90" />
      <el-table-column prop="taskType" label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.taskType==='rest'?'info':row.taskType==='cardio'?'warning':'success'">
            {{ typeLabel(row.taskType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="内容" min-width="240" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'DONE'" size="small" type="success" @click="checkIn(row, 'DONE')">完成</el-button>
            <el-button v-if="row.status === 'TODO'" size="small" @click="checkIn(row, 'SKIP')">跳过</el-button>
            <el-button v-if="row.status !== 'TODO'" size="small" text @click="checkIn(row, 'UNDO')">撤销</el-button>
          </template>
        </el-table-column>
    </el-table>
  </div>
</div>
  `,
};
