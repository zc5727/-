/**
 * 会员智能运营中枢 —— 补上原系统只有 CRUD、没有"经营判断"的那一块。
 *
 * 三块内容：
 *   1. 分层分布：按 RFM 把会员划入高价值 / 成长 / 沉睡 / 流失风险，给的是可排序的清单，
 *      解决"谁该重点维护"只能靠拍脑袋的问题。
 *   2. 流失预警：列出具体信号（多少天没来、多久没充值），而不是一个无从验证的概率数字。
 *   3. 器材工单：PLANNED→ASSIGNED→DONE→VERIFIED 的状态闭环，带负责人与成本。
 *
 * 数据来自业务库的运算结果表，原始会员/签到/充值表不做任何改动。
 */
import { gymApi } from '../api.js';

const { ElMessage } = ElementPlus;

const SEGMENT_LABEL = {
  HIGH_VALUE: '高价值',
  GROWING: '成长',
  SLEEPING: '沉睡',
  CHURN_RISK: '流失风险',
};

const STATUS_LABEL = {
  PLANNED: '待派工',
  ASSIGNED: '已派工',
  DONE: '已完成',
  VERIFIED: '已验收',
};

const NEXT_STATUS = {
  PLANNED: 'ASSIGNED',
  ASSIGNED: 'DONE',
  DONE: 'VERIFIED',
};

export default {
  name: 'OpsPage',
  data() {
    return {
      days: 30,
      loading: false,
      ov: null,
      segments: [],
      segmentFilter: '',
      orders: [],
      orderStatusFilter: '',
      createVisible: false,
      createForm: { equipmentNo: '', planType: 'ROUTINE', title: '', plannedDate: '', operator: '' },
      logsVisible: false,
      logsTitle: '',
      logs: [],
    };
  },
  mounted() {
    this.$nextTick(() => {
      this.loadOverview();
      this.loadSegments();
      this.loadOrders();
    });
  },
  computed: {
    segmentOptions() {
      return Object.entries(SEGMENT_LABEL).map(([value, label]) => ({ value, label }));
    },
  },
  methods: {
    segLabel(v) {
      return SEGMENT_LABEL[v] || v || '-';
    },
    statusLabel(v) {
      return STATUS_LABEL[v] || v || '-';
    },
    nextLabel(status) {
      const n = NEXT_STATUS[status];
      return n ? '推进为' + STATUS_LABEL[n] : '已终态';
    },
    canAdvance(status) {
      return !!NEXT_STATUS[status];
    },
    async loadOverview() {
      this.loading = true;
      try {
        this.ov = await gymApi.opsOverview(this.days);
      } catch (e) {
        ElMessage.error('加载运营概览失败：' + e.message);
      } finally {
        this.loading = false;
      }
    },
    async loadSegments() {
      try {
        const r = await gymApi.opsSegments(this.segmentFilter || '');
        this.segments = (r && r.items) || [];
      } catch (e) {
        ElMessage.error('加载分层名单失败：' + e.message);
      }
    },
    async refreshOps() {
      try {
        const r = await gymApi.opsRefresh(90);
        if (r && r.available) {
          ElMessage.success('已重算：分层 ' + r.segments + ' 人 / 信号 ' + r.signals + ' 条 / 快照 ' + r.snapshots + ' 天');
          await this.loadOverview();
          await this.loadSegments();
          await this.loadOrders();
        } else {
          ElMessage.warning('重算未生效：' + (r && r.reason));
        }
      } catch (e) {
        ElMessage.error('重算失败：' + e.message);
      }
    },
    async loadOrders() {
      try {
        const r = await gymApi.equipmentOrders(this.orderStatusFilter || '');
        this.orders = (r && r.items) || [];
      } catch (e) {
        this.orders = [];
      }
    },
    async submitCreate() {
      try {
        const r = await gymApi.equipmentOrderCreate(this.createForm);
        if (r && r.ok) {
          ElMessage.success('工单已创建：#' + r.orderId);
          this.createVisible = false;
          this.createForm = { equipmentNo: '', planType: 'ROUTINE', title: '', plannedDate: '', operator: '' };
          await this.loadOrders();
        } else {
          ElMessage.warning('创建失败：' + (r && r.reason));
        }
      } catch (e) {
        ElMessage.error('创建失败：' + e.message);
      }
    },
    async advance(row) {
      try {
        const to = NEXT_STATUS[row.status];
        const r = await gymApi.equipmentOrderAdvance(row.id, {
          to,
          assignee: row.assignee || '',
          operator: row.operator || 'admin',
        });
        if (r && r.ok) {
          ElMessage.success('已推进为：' + STATUS_LABEL[to]);
          await this.loadOrders();
        } else {
          ElMessage.warning('推进失败：' + (r && r.reason || '状态已变更，请刷新'));
        }
      } catch (e) {
        ElMessage.error('推进失败：' + e.message);
      }
    },
    async openLogs(row) {
      this.logsTitle = '工单 #' + row.id + ' 流转日志';
      try {
        const r = await gymApi.equipmentOrderLogs(row.id);
        this.logs = (r && r.items) || [];
      } catch (e) {
        this.logs = [];
      }
      this.logsVisible = true;
    },
    fmtDate(v) {
      if (!v) return '-';
      const s = String(v);
      return s.length > 10 ? s.slice(0, 10) : s;
    },
  },
  template: `
<div class="page-wrap">
  <div class="page-head">
    <div>
      <h2>会员智能运营中枢</h2>
      <p class="muted">RFM 分层、流失预警与器材维护工单闭环。</p>
    </div>
    <div class="toolbar">
      <span class="muted">趋势窗口</span>
      <el-select v-model="days" size="small" style="width:110px" @change="loadOverview">
        <el-option :value="7" label="近 7 天" />
        <el-option :value="30" label="近 30 天" />
        <el-option :value="90" label="近 90 天" />
      </el-select>
      <el-button size="small" type="primary" @click="refreshOps">重算全部指标</el-button>
    </div>
  </div>

  <div v-if="ov && !ov.available" class="empty-box">
    运营中枢不可用：{{ ov.reason }}
  </div>

  <div v-if="ov && ov.available">
    <div class="kpi-row">
      <div class="kpi" v-for="c in (ov.segmentCounts||[])" :key="c.segment">
        <div class="kpi-v">{{ c.cnt }}</div>
        <div class="kpi-l">{{ segLabel(c.segment) }}</div>
      </div>
    </div>

    <div class="card">
      <div class="card-h">流失风险榜 TOP 20</div>
      <el-table :data="(ov.churnRanking||[])" size="small" border stripe>
        <el-table-column prop="memberNo" label="会员编号" width="110" />
        <el-table-column prop="riskScore" label="风险分" width="100" />
        <el-table-column prop="signalCount" label="信号数" width="90" />
      </el-table>
    </div>

    <div class="card">
      <div class="card-h">分层名单</div>
      <div class="toolbar">
        <el-select v-model="segmentFilter" size="small" clearable placeholder="全部层级" style="width:150px" @change="loadSegments">
          <el-option v-for="o in segmentOptions" :key="o.value" :value="o.value" :label="o.label" />
        </el-select>
      </div>
      <el-table :data="segments" size="small" border stripe max-height="420">
        <el-table-column prop="memberNo" label="会员" width="90" />
        <el-table-column prop="memberName" label="姓名" width="120" />
        <el-table-column prop="segment" label="层级" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.segment==='HIGH_VALUE'?'success':row.segment==='CHURN_RISK'?'danger':'info'">
              {{ segLabel(row.segment) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="综合得分" width="100" />
        <el-table-column prop="recencyDays" label="距最近到店(天)" width="130" />
        <el-table-column prop="frequency" label="近90天到店" width="110" />
        <el-table-column prop="monetary" label="近90天充值" width="120" />
      </el-table>
    </div>

    <div class="card">
      <div class="card-h">
        器材维护工单
        <el-button size="small" style="margin-left:12px" @click="createVisible = true">新建工单</el-button>
      </div>
      <div class="toolbar">
        <el-select v-model="orderStatusFilter" size="small" clearable placeholder="全部状态" style="width:140px" @change="loadOrders">
          <el-option value="PLANNED" label="待派工" />
          <el-option value="ASSIGNED" label="已派工" />
          <el-option value="DONE" label="已完成" />
          <el-option value="VERIFIED" label="已验收" />
        </el-select>
      </div>
      <el-table :data="orders" size="small" border stripe>
        <el-table-column prop="id" label="工单号" width="90" />
        <el-table-column prop="equipmentName" label="器材" min-width="140" />
        <el-table-column prop="planType" label="类型" width="100">
          <template #default="{ row }">
            {{ row.planType === 'REPAIR' ? '故障维修' : '例行保养' }}
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assignee" label="负责人" width="110" />
        <el-table-column prop="cost" label="成本" width="90" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :disabled="!canAdvance(row.status)" @click="advance(row)">
              {{ nextLabel(row.status) }}
            </el-button>
            <el-button size="small" text @click="openLogs(row)">日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>

  <el-dialog v-model="createVisible" title="新建器材维护工单" width="480px">
    <el-form :model="createForm" label-width="96px">
      <el-form-item label="器材编号"><el-input v-model="createForm.equipmentNo" placeholder="如 1" /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="createForm.planType" style="width:100%">
          <el-option value="ROUTINE" label="例行保养" />
          <el-option value="REPAIR" label="故障维修" />
        </el-select>
      </el-form-item>
      <el-form-item label="标题"><el-input v-model="createForm.title" placeholder="如：跑步机异响检修" /></el-form-item>
      <el-form-item label="计划日期"><el-input v-model="createForm.plannedDate" placeholder="YYYY-MM-DD" /></el-form-item>
      <el-form-item label="操作人"><el-input v-model="createForm.operator" placeholder="admin" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button size="small" @click="createVisible = false">取消</el-button>
      <el-button size="small" type="primary" @click="submitCreate">创建</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="logsVisible" :title="logsTitle" size="42%">
    <el-timeline v-if="logs.length">
      <el-timeline-item v-for="(l, i) in logs" :key="i"
                        :timestamp="fmtDate(l.createdAt)" placement="top">
        <strong>{{ l.action }}</strong>
        <span v-if="l.operator" class="muted"> · {{ l.operator }}</span>
        <div v-if="l.note" class="muted">{{ l.note }}</div>
      </el-timeline-item>
    </el-timeline>
    <div v-else class="empty-box">暂无流转记录</div>
  </el-drawer>
</div>
  `,
};
