/**
 * 数据看板：会员规模 + 经营指标 + 可视化图表。
 * 图表使用 ECharts，颜色遵循品牌色板。
 */
import { gymApi } from '../api.js';

const { ElMessage } = ElementPlus;

export default {
  name: 'DashboardPage',
  data() {
    return {
      loading: true,
      year: 2023,
      stats: {
        members: 0,
        courses: 0,
        employees: 0,
        equipment: 0,
        registers: 0,
      },
    };
  },
  mounted() {
    this.loadStats();
    this.$nextTick(() => this.renderCharts());
    window.addEventListener('resize', this.onResize);
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.onResize);
    if (this.barChart) this.barChart.dispose();
    if (this.pieChart) this.pieChart.dispose();
  },
  methods: {
    async loadStats() {
      this.loading = true;
      try {
        const [m, c, e, eq, r] = await Promise.all([
          gymApi.memberTotal(),
          gymApi.courseTotal(),
          gymApi.employeeTotal(),
          gymApi.equipmentTotal(),
          gymApi.registerTotal(),
        ]);
        this.stats = {
          members: m?.dataTotal ?? 0,
          courses: c?.dataTotal ?? 0,
          employees: e?.dataTotal ?? 0,
          equipment: eq?.dataTotal ?? 0,
          registers: r?.dataTotal ?? 0,
        };
      } catch (err) {
        ElMessage.error(`加载统计失败：${err.message}`);
      } finally {
        this.loading = false;
      }
    },

    onResize() {
      if (this.barChart) this.barChart.resize();
      if (this.pieChart) this.pieChart.resize();
    },

    async renderCharts() {
      await Promise.all([this.renderBar(), this.renderPie()]);
    },

    async renderBar() {
      const el = this.$refs.barRef;
      if (!el) return;
      this.barChart = this.barChart || echarts.init(el);
      this.barChart.showLoading({ text: '加载中', color: '#6366f1', textColor: '#64748b' });

      let seasons = ['一季度', '二季度', '三季度', '四季度'];
      let counts = [0, 0, 0, 0];
      try {
        const res = await gymApi.memberBySeason(this.year);
        if (res?.season) seasons = res.season;
        if (res?.count) counts = res.count;
      } catch (err) {
        ElMessage.error(`加载开卡数据失败：${err.message}`);
      }
      this.barChart.hideLoading();
      this.barChart.setOption({
        color: ['#6366f1'],
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: 12, right: 20, top: 28, bottom: 8, containLabel: true },
        xAxis: {
          type: 'category',
          data: seasons,
          axisLine: { lineStyle: { color: '#e2e8f0' } },
          axisTick: { show: false },
          axisLabel: { color: '#64748b' },
        },
        yAxis: {
          type: 'value',
          splitLine: { lineStyle: { color: '#f1f5f9' } },
          axisLabel: { color: '#94a3b8' },
        },
        series: [{
          name: '开卡数',
          type: 'bar',
          data: counts,
          barWidth: '42%',
          itemStyle: {
            borderRadius: [7, 7, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#818cf8' },
              { offset: 1, color: '#4f46e5' },
            ]),
          },
          label: { show: true, position: 'top', color: '#64748b', fontSize: 11 },
        }],
      }, true);
    },

    async renderPie() {
      const el = this.$refs.pieRef;
      if (!el) return;
      this.pieChart = this.pieChart || echarts.init(el);
      this.pieChart.showLoading({ text: '加载中', color: '#06b6d4', textColor: '#64748b' });

      let data = [];
      try {
        const res = await gymApi.memberSexByYear(this.year);
        data = Array.isArray(res?.sexCount) ? res.sexCount : [];
      } catch (err) {
        ElMessage.error(`加载性别分布失败：${err.message}`);
      }
      this.pieChart.hideLoading();

      if (!data.length) {
        this.pieChart.clear();
        this.pieChart.setOption({
          title: {
            text: '暂无数据', left: 'center', top: 'middle',
            textStyle: { color: '#94a3b8', fontSize: 13, fontWeight: 'normal' },
          },
        });
        return;
      }

      this.pieChart.setOption({
        color: ['#6366f1', '#06b6d4', '#10b981', '#f59e0b'],
        tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）' },
        legend: { bottom: 4, icon: 'circle', textStyle: { color: '#64748b' } },
        series: [{
          type: 'pie',
          radius: ['48%', '70%'],
          center: ['50%', '46%'],
          avoidLabelOverlap: true,
          itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
          label: { formatter: '{b}\n{d}%', color: '#475569', fontSize: 12 },
          labelLine: { length: 10, length2: 8 },
          data,
        }],
      }, true);
    },
  },
  template: `
    <div>
      <div class="page-head">
        <div>
          <h1>数据看板</h1>
          <p>会员规模、资源分布与开卡趋势一览</p>
        </div>
        <div class="page-head-actions">
          <el-select v-model="year" style="width:118px" @change="renderCharts">
            <el-option :value="2023" label="2023 年" />
            <el-option :value="2024" label="2024 年" />
          </el-select>
          <el-button @click="loadStats(); renderCharts()">
            <app-icon name="refresh" :size="15" /> 刷新
          </el-button>
        </div>
      </div>

      <div class="stat-grid" v-loading="loading">
        <div class="stat">
          <div class="stat-ico i1"><app-icon name="users" :size="22" /></div>
          <div>
            <div class="stat-label">会员总数</div>
            <div class="stat-value">{{ stats.members }}<small>人</small></div>
            <div class="stat-foot">全量在册会员档案</div>
          </div>
        </div>
        <div class="stat">
          <div class="stat-ico i2"><app-icon name="course" :size="22" /></div>
          <div>
            <div class="stat-label">课程数量</div>
            <div class="stat-value">{{ stats.courses }}<small>门</small></div>
            <div class="stat-foot">在售课程</div>
          </div>
        </div>
        <div class="stat">
          <div class="stat-ico i3"><app-icon name="badge" :size="22" /></div>
          <div>
            <div class="stat-label">员工人数</div>
            <div class="stat-value">{{ stats.employees }}<small>人</small></div>
            <div class="stat-foot">教练与运营团队</div>
          </div>
        </div>
        <div class="stat">
          <div class="stat-ico i4"><app-icon name="box" :size="22" /></div>
          <div>
            <div class="stat-label">器材台账</div>
            <div class="stat-value">{{ stats.equipment }}<small>台</small></div>
            <div class="stat-foot">在册器材设备</div>
          </div>
        </div>
        <div class="stat">
          <div class="stat-ico i5"><app-icon name="clipboard" :size="22" /></div>
          <div>
            <div class="stat-label">课程报名</div>
            <div class="stat-value">{{ stats.registers }}<small>笔</small></div>
            <div class="stat-foot">累计报名记录</div>
          </div>
        </div>
      </div>

      <div class="grid-2">
        <div class="card">
          <div class="card-head">
            <h3>各季度开卡数</h3>
            <span class="sub">按会员开卡时间统计</span>
          </div>
          <div class="card-body">
            <div ref="barRef" style="height:288px"></div>
          </div>
        </div>
        <div class="card">
          <div class="card-head">
            <h3>会员性别分布</h3>
            <span class="sub">{{ year }} 年</span>
          </div>
          <div class="card-body">
            <div ref="pieRef" style="height:288px"></div>
          </div>
        </div>
      </div>
    </div>
  `,
};
