/**
 * 知识库页面 —— 给"看内容"和"查检索"两种用途各留一块。
 *
 * 为什么要有这一页：RAG 出错时，第一件想做的事是"它到底检索到了什么"。
 * 这一页把检索命中的篇目、章节、得分直接摆出来，再提供全文浏览，
 * 于是"知识库里有没有、写得对不对、检索准不准"这三件事都能当场验证，
 * 不必进容器去翻 Markdown。
 */
import { agentApi } from '../api.js';

const { ElMessage } = ElementPlus;

const CATEGORIES = [
  { value: '', label: '全部' },
  { value: 'exercise', label: '动作库' },
  { value: 'training', label: '训练基础' },
  { value: 'plan', label: '训练计划' },
  { value: 'nutrition', label: '营养' },
  { value: 'safety', label: '安全' },
];

const RISK_LABEL = { low: '低', medium: '中', high: '高' };

export default {
  name: 'KnowledgePage',
  data() {
    return {
      stats: null,
      categories: CATEGORIES,
      category: '',
      docs: [],
      docsLoading: false,
      keyword: '',
      searching: false,
      searchQuery: '',
      hits: [],
      searched: false,
      detailVisible: false,
      detail: null,
      detailLoading: false,
    };
  },
  computed: {
    filteredDocs() {
      const k = this.keyword.trim().toLowerCase();
      if (!k) return this.docs;
      return this.docs.filter((d) => {
        const hay = [d.id, d.title, (d.tags || []).join(' '), (d.questions || []).join(' ')]
          .join(' ')
          .toLowerCase();
        return hay.includes(k);
      });
    },
    byCategory() {
      return (this.stats && this.stats.byCategory) || {};
    },
    totalChars() {
      return (this.stats && this.stats.chars) || 0;
    },
  },
  mounted() {
    this.loadStats();
    this.loadDocs();
  },
  methods: {
    async loadStats() {
      try {
        this.stats = await agentApi.knowledgeStats();
      } catch (e) {
        ElMessage.error('读取知识库概况失败：' + e.message);
      }
    },

    async loadDocs() {
      this.docsLoading = true;
      try {
        const data = await agentApi.knowledgeDocs(this.category || undefined);
        this.docs = Array.isArray(data) ? data : [];
      } catch (e) {
        this.docs = [];
        ElMessage.error('读取文档目录失败：' + e.message);
      } finally {
        this.docsLoading = false;
      }
    },

    onCategory(value) {
      this.category = value;
      this.loadDocs();
    },

    async runSearch() {
      const q = this.searchQuery.trim();
      if (!q) {
        ElMessage.warning('请输入要检索的内容');
        return;
      }
      this.searching = true;
      this.searched = false;
      try {
        const res = await agentApi.knowledgeSearch(q, 5);
        this.hits = (res && res.hits) || [];
        this.searched = true;
      } catch (e) {
        this.hits = [];
        ElMessage.error('检索失败：' + e.message);
      } finally {
        this.searching = false;
      }
    },

    async openDetail(row) {
      this.detailVisible = true;
      this.detail = null;
      this.detailLoading = true;
      try {
        this.detail = await agentApi.knowledgeDoc(row.id);
      } catch (e) {
        ElMessage.error('读取文档失败：' + e.message);
        this.detailVisible = false;
      } finally {
        this.detailLoading = false;
      }
    },

    sectionEntries(map) {
      return Object.entries(map || {});
    },

    riskText(v) {
      return RISK_LABEL[v] || v || '-';
    },

    /** 检索得分：阈值以下视为"答不上来"，列表里也据此标色，和线上判定保持一致 */
    isBelowThreshold(score) {
      const min = (this.stats && this.stats.minScore) || 9;
      return Number(score) < Number(min);
    },
  },
  template: `
    <div>
      <div class="page-head">
        <div>
          <h1>知识库</h1>
          <p>智能问答的资料来源 —— 训练、动作、计划、营养、安全五类，全部为站内 Markdown 文档</p>
        </div>
        <div class="page-head-actions">
          <el-button @click="loadStats(); loadDocs()">
            <app-icon name="refresh" :size="15" /> 刷新
          </el-button>
        </div>
      </div>

      <!-- 概况 -->
      <div class="stat-grid" v-if="stats">
        <div class="stat">
          <div class="stat-label">文档总数</div>
          <div class="stat-value">{{ stats.total }}<small>篇</small></div>
          <div class="stat-foot">目录 {{ stats.dir }}</div>
        </div>
        <div class="stat">
          <div class="stat-label">正文规模</div>
          <div class="stat-value">{{ totalChars }}<small>字</small></div>
          <div class="stat-foot">{{ stats.sections }} 个章节</div>
        </div>
        <div class="stat">
          <div class="stat-label">分类分布</div>
          <div class="stat-value" style="font-size:15px">
            <span v-for="(n, k) in byCategory" :key="k" class="chip brand" style="margin-right:5px">{{ k }} {{ n }}</span>
          </div>
          <div class="stat-foot">动作库 / 训练基础 / 计划 / 营养 / 安全</div>
        </div>
        <div class="stat">
          <div class="stat-label">命中阈值</div>
          <div class="stat-value">{{ stats.minScore }}</div>
          <div class="stat-foot">低于它的问题走兜底话术，不硬答</div>
        </div>
      </div>

      <!-- 检索测试 -->
      <div class="card" style="margin-top:16px">
        <div class="card-head">
          <h3>检索验证</h3>
          <span class="sub">看看一个真实问题会被哪几篇文档命中、拿到哪一段、得多少分</span>
        </div>
        <div class="toolbar">
          <el-input v-model="searchQuery" placeholder="例如：深蹲膝盖疼怎么办 / 蛋白质吃多少 / 训练前热身"
            style="max-width:420px" clearable @keyup.enter="runSearch" />
          <el-button type="primary" :loading="searching" @click="runSearch">
            <app-icon name="search" :size="15" /> 检索
          </el-button>
          <div class="spacer"></div>
          <span class="muted" style="font-size:12.5px" v-if="searched">
            命中 {{ hits.length }} 篇
          </span>
        </div>

        <el-table :data="hits" v-if="searched" style="width:100%"
          empty-text="没有命中任何文档 —— 这个问题会被判为「答不上来」">
          <el-table-column label="来源" min-width="200">
            <template #default="scope">
              <div style="font-weight:600">{{ scope.row.title }}</div>
              <div class="muted" style="font-size:11.5px">
                {{ scope.row.categoryLabel }} · {{ scope.row.id }}
                <span v-if="scope.row.anchor">· {{ scope.row.anchor }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="得分" width="110" align="right">
            <template #default="scope">
              <span class="chip" :class="isBelowThreshold(scope.row.score) ? 'warn' : 'success'">
                {{ scope.row.score }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="引用片段" min-width="320">
            <template #default="scope">
              <div style="font-size:12.5px;line-height:1.7;color:var(--ink-2)">{{ scope.row.snippet }}</div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 文档目录 -->
      <div class="card" style="margin-top:16px">
        <div class="card-head">
          <h3>文档目录</h3>
          <span class="sub">点任意一行查看全文；文档以卷挂载，改完 Markdown 一个刷新周期内即可生效</span>
        </div>
        <div class="toolbar">
          <el-radio-group :model-value="category" @update:model-value="onCategory">
            <el-radio-button v-for="c in categories" :key="c.value" :value="c.value">{{ c.label }}</el-radio-button>
          </el-radio-group>
          <div class="spacer"></div>
          <el-input v-model="keyword" placeholder="按标题 / 标签 / 关联问题筛选" style="max-width:300px" clearable />
        </div>

        <el-table :data="filteredDocs" v-loading="docsLoading" style="width:100%"
          empty-text="该分类下暂无文档" @row-click="openDetail">
          <el-table-column prop="id" label="标识" width="150" />
          <el-table-column label="标题" min-width="200">
            <template #default="scope">
              <div style="font-weight:600;color:var(--brand-600);cursor:pointer">{{ scope.row.title }}</div>
              <div class="muted" style="font-size:11.5px">{{ scope.row.summary }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="categoryLabel" label="分类" width="94" />
          <el-table-column label="难度" width="80">
            <template #default="scope">{{ scope.row.levelLabel || '-' }}</template>
          </el-table-column>
          <el-table-column label="风险" width="80">
            <template #default="scope">
              <span class="chip" :class="scope.row.riskLevel === 'high' ? 'danger' : (scope.row.riskLevel === 'medium' ? 'warn' : 'success')">
                {{ riskText(scope.row.riskLevel) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="标签" min-width="150">
            <template #default="scope">
              <span v-for="t in (scope.row.tags || []).slice(0, 4)" :key="t" class="chip" style="margin-right:4px">{{ t }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="chars" label="字数" width="80" align="right" />
        </el-table>
      </div>

      <el-drawer v-model="detailVisible" size="620px" :title="detail ? detail.title : '文档详情'">
        <div v-loading="detailLoading" style="min-height:120px">
          <template v-if="detail">
            <div class="muted" style="font-size:12.5px;margin-bottom:12px">
              {{ detail.id }} · {{ detail.categoryLabel }}
              <span v-if="detail.levelLabel">· {{ detail.levelLabel }}</span>
              <span v-if="detail.equipment && detail.equipment.length">· 器械：{{ detail.equipment.join('、') }}</span>
              <span v-if="detail.primaryMuscles && detail.primaryMuscles.length">· 主要肌群：{{ detail.primaryMuscles.join('、') }}</span>
            </div>
            <div v-if="detail.questions && detail.questions.length" style="margin-bottom:14px">
              <div style="font-size:12.5px;font-weight:600;margin-bottom:6px">覆盖的问题</div>
              <div>
                <span v-for="q in detail.questions" :key="q" class="chip brand" style="margin:0 5px 5px 0">{{ q }}</span>
              </div>
            </div>
            <div v-for="[name, text] in sectionEntries(detail.sections)" :key="name" style="margin-bottom:14px">
              <div style="font-size:13px;font-weight:600;margin-bottom:5px">{{ name }}</div>
              <div style="font-size:13px;line-height:1.85;color:var(--ink-2);white-space:pre-wrap">{{ text }}</div>
            </div>
            <div class="muted" style="font-size:11.5px;margin-top:16px">文件：{{ detail.path }}</div>
          </template>
        </div>
      </el-drawer>
    </div>
  `,
};
