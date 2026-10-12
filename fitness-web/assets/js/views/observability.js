/**
 * 运行观测页 —— 回答"这套智能体现在跑得怎么样"。
 *
 * 四块内容，从上到下是"结论 → 归因 → 明细"的顺序：
 *   1. KPI 卡：调用量 / 失败率 / 降级率 / Token 消耗 / 延迟分位
 *   2. 分布图：意图占比与状态占比，用来回答"都在问什么、有多少没答好"
 *   3. 趋势图：按天的调用量与降级数
 *   4. 流水明细：最近调用，每条画出内部阶段瀑布图
 *
 * 瀑布图那一段是这一页最有价值的部分：一次对话慢在哪一步
 * （是在等大模型、还是在做检索、还是在落库），看一眼条的位置就知道，
 * 不需要回到日志里去拼时间线。
 */
import { agentApi } from '../api.js';

const { ElMessage } = ElementPlus;

export default {
  name: 'ObservabilityPage',
  data() {
    return {
      days: 7,
      limit: 20,
      loading: false,
      ov: null,
      traces: [],
      intentFilter: '',
      statusFilter: '',
      chart: null,
      chart2: null,
      chart3: null,
    };
  },
  mounted() {
    this.$nextTick(() => this.load());
  },
  beforeUnmount() {
    [this.chart, this.chart2, this.chart3].forEach((c) => c && c.dispose());
  },
  methods: {
    async load() {
      this.loading = true;
      try {
        const [ov, tr] = await Promise.all([
          agentApi.observabilityOverview(this.days),
          agentApi.observabilityTraces(this.limit, this.intentFilter, this.statusFilter),
        ]);
        this.ov = ov;
        this.traces = (tr && tr.items) || [];
        this.$nextTick(() => this.draw());
      } catch (e) {
        ElMessage.error('加载运行指标失败：' + e.message);
      } finally {
        this.loading = false;
      }
    },
    async refreshDaily() {
      try {
        const r = await agentApi.observabilityRefreshDaily(30);
        ElMessage.success(r && r.available ? '日指标已刷新：' + (r.metricsWritten || 0) + ' 项' : '刷新未生效');
        await this.load();
      } catch (e) {
        ElMessage.error('刷新失败：' + e.message);
      }
    },
    draw() {
      const ov = this.ov;
      if (!ov || !ov.available) return;

      // 图 1：延迟分位对比
      const el = this.$refs.latencyChart;
      if (el) {
        this.chart = this.chart || echarts.init(el);
        this.chart.setOption({
          tooltip: {},
          grid: { left: 50, right: 20, top: 30, bottom: 30 },
          xAxis: { type: 'category', data: ['P50', 'P95', 'P99', '平均'] },
          yAxis: { type: 'value', name: '毫秒' },
          series: [{
            type: 'bar',
            barWidth: '45%',
            data: [ov.p50, ov.p95, ov.p99, ov.avgLatency],
            itemStyle: { color: '#111' },
          }],
        }, true);
      }

      // 图 2：意图分布
      const el2 = this.$refs.intentChart;
      if (el2 && ov.byIntent && ov.byIntent.length) {
        this.chart2 = this.chart2 || echarts.init(el2);
        this.chart2.setOption({
          tooltip: { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' },
          series: [{
            type: 'pie',
            radius: ['42%', '68%'],
            data: ov.byIntent.map((x) => ({ name: x.intent || '(未识别)', value: x.c })),
            label: { fontSize: 11 },
          }],
        }, true);
      }

      // 图 3：按天趋势
      const el3 = this.$refs.trendChart;
      if (el3 && ov.byDay && ov.byDay.length) {
        this.chart3 = this.chart3 || echarts.init(el3);
        this.chart3.setOption({
          tooltip: { trigger: 'axis' },
          legend: { data: ['调用量', '降级数'], bottom: 0, itemWidth: 12, itemHeight: 8 },
          grid: { left: 50, right: 20, top: 20, bottom: 40 },
          xAxis: { type: 'category', data: ov.byDay.map((x) => String(x.d)) },
          yAxis: { type: 'value' },
          series: [
            { name: '调用量', type: 'line', smooth: true, data: ov.byDay.map((x) => x.c), itemStyle: { color: '#111' } },
            { name: '降级数', type: 'bar', barWidth: '35%', data: ov.byDay.map((x) => x.g), itemStyle: { color: '#999' } },
          ],
        }, true);
      }
    },
    barLeft(span, total) {
      if (!total) return '0%';
      return ((span.start_ms / total) * 100).toFixed(2) + '%';
    },
    barWidth(span, total) {
      if (!total) return '0%';
      const w = (span.duration_ms / total) * 100;
      return Math.max(w, 0.8).toFixed(2) + '%';
    },
    kindColor(kind) {
      return { llm: '#111', retrieve: '#666', db: '#999', tool: '#bbb' }[kind] || '#888';
    },
  },
  template: `
<div class="page-wrap">
  <div class="page-head">
    <div>
      <h2>运行观测</h2>
      <p class="muted">调用链路、延迟分布与降级情况的实时统计。</p>
    </div>
    <div class="toolbar">
      <span class="muted">统计窗口</span>
      <el-select v-model="days" size="small" style="width:110px" @change="load">
        <el-option :value="1" label="今天" />
        <el-option :value="7" label="近 7 天" />
        <el-option :value="30" label="近 30 天" />
      </el-select>
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      <el-button size="small" @click="refreshDaily">重算日指标</el-button>
    </div>
  </div>

  <div v-if="ov && !ov.available" class="empty-box">
    观测不可用：{{ ov.reason || '数据库未连接' }}（对话功能不受影响）
  </div>

  <div v-if="ov && ov.available">
    <div class="kpi-row">
      <div class="kpi"><div class="kpi-v">{{ ov.calls }}</div><div class="kpi-l">总调用</div></div>
      <div class="kpi"><div class="kpi-v">{{ ov.errorRate }}%</div><div class="kpi-l">失败率</div></div>
      <div class="kpi"><div class="kpi-v">{{ ov.degradedRate }}%</div><div class="kpi-l">降级率</div></div>
      <div class="kpi"><div class="kpi-v">{{ ov.tokens }}</div><div class="kpi-l">Token 消耗</div></div>
      <div class="kpi"><div class="kpi-v">{{ ov.p95 }}<span class="unit">ms</span></div><div class="kpi-l">P95 延迟</div></div>
      <div class="kpi"><div class="kpi-v">{{ ov.knowledgeHitRate }}%</div><div class="kpi-l">知识命中率</div></div>
    </div>

    <div class="chart-2col">
      <div class="card">
        <div class="card-h">意图分布</div>
        <div ref="intentChart" style="height:220px"></div>
      </div>
      <div class="card">
        <div class="card-h">延迟分位</div>
        <div ref="latencyChart" style="height:220px"></div>
      </div>
    </div>

    <div class="card">
      <div class="card-h">按天趋势</div>
      <div ref="trendChart" class="chart-box" style="height:240px"></div>
    </div>

    <div class="card">
      <div class="card-h">最近调用（含阶段瀑布图）</div>
      <div class="toolbar">
        <el-select v-model="intentFilter" size="small" clearable placeholder="意图" style="width:170px" @change="load">
          <el-option v-for="i in (ov.byIntent||[])" :key="i.intent" :value="i.intent" :label="i.intent" />
        </el-select>
        <el-select v-model="statusFilter" size="small" clearable placeholder="状态" style="width:130px" @change="load">
          <el-option value="OK" label="OK" />
          <el-option value="ASK" label="ASK" />
          <el-option value="DEGRADED" label="DEGRADED" />
          <el-option value="ERROR" label="ERROR" />
        </el-select>
      </div>

      <div v-if="!traces.length" class="empty-box">暂无调用记录</div>
      <div v-for="t in traces" :key="t.trace_id" class="trace-item">
        <div class="trace-top">
          <span class="tag">{{ t.intent || 'UNKNOWN' }}</span>
          <span class="tag ghost">{{ t.status }}</span>
          <span class="tag ghost">{{ t.channel }}</span>
          <span class="spacer"></span>
          <span class="mono">{{ t.latency_ms }} ms</span>
        </div>
        <div class="trace-q">{{ t.user_text || '(空)' }}</div>
        <div class="wf">
          <div v-for="(s, i) in (t.spans||[])" :key="i" class="wf-row">
            <span class="wf-name">{{ s.span_name }}</span>
            <span class="wf-track">
              <span class="wf-bar"
                    :style="{ left: barLeft(s, t.latency_ms), width: barWidth(s, t.latency_ms), background: kindColor(s.kind) }"></span>
            </span>
            <span class="wf-dur mono">{{ s.duration_ms }} ms</span>
          </div>
          <div v-if="!(t.spans||[]).length" class="muted">（无阶段记录）</div>
        </div>
      </div>
    </div>
  </div>
</div>
  `,
};
