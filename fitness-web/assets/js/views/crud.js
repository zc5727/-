/**
 * 通用 CRUD 页工厂。
 *
 * 各管理页（会员/课程/器材/员工/报名）的交互结构完全一致：
 * 搜索 → 分页表格 → 新增/编辑弹窗 → 删除确认。
 * 因此抽成一个工厂，各页只声明「列」与「表单字段」，
 * 避免五份几乎相同的模板代码各自演化出不一致的行为。
 */

const { ElMessage, ElMessageBox } = ElementPlus;

export function createCrudPage(config) {
  const {
    title = '',
    subtitle = '',
    columns = [],
    formFields = [],
    api = {},
    idKey = 'id',
    pageSize = 10,
    searchPlaceholder = '请输入关键词',
    createLabel = '新增',
    defaultForm = () => ({}),
  } = config;

  return {
    name: 'CrudPage',
    data() {
      return {
        loading: false,
        rows: [],
        total: 0,
        pageNo: 1,
        pageSize,
        keyWord: '',
        dialogVisible: false,
        saving: false,
        isEdit: false,
        form: defaultForm(),
        config,
        /* 关键：模板表达式只能访问组件实例属性，
           工厂闭包里的 columns / formFields / title 等必须显式暴露，
           否则渲染出来全是空（列头、占位符、标题全丢）。 */
        title,
        subtitle,
        columns,
        formFields,
        searchPlaceholder,
        createLabel,
      };
    },
    computed: {
      pageOffset() {
        return (this.pageNo - 1) * this.pageSize;
      },
      dialogTitle() {
        return this.isEdit ? `编辑${title}` : `新增${title}`;
      },
    },
    mounted() {
      // 列表与总数一起拉，否则首屏「共 N 条」永远是 0
      this.refresh();
    },
    methods: {
      /** 普通列表：page 为偏移量 */
      async load() {
        this.loading = true;
        try {
          const data = this.keyWord
            ? await api.search(this.keyWord, this.pageOffset, this.pageSize)
            : await api.list(this.pageOffset, this.pageSize);
          this.rows = Array.isArray(data) ? data : [];
        } catch (e) {
          this.rows = [];
          ElMessage.error(`加载${title}失败：${e.message}`);
        } finally {
          this.loading = false;
        }
      },

      /** 总数单独查，兼容模糊查询的总数接口 */
      async loadTotal() {
        try {
          const res = this.keyWord
            ? await api.totalFuzzy(this.keyWord)
            : await api.total();
          this.total = Number(res && res.dataTotal != null ? res.dataTotal : 0);
        } catch (_) {
          this.total = 0;
        }
      },

      async refresh() {
        await Promise.all([this.load(), this.loadTotal()]);
      },

      onSearch() {
        this.pageNo = 1;
        this.refresh();
      },

      onReset() {
        this.keyWord = '';
        this.pageNo = 1;
        this.refresh();
      },

      onPageChange(p) {
        this.pageNo = p;
        this.load();
      },

      onSizeChange(s) {
        this.pageSize = s;
        this.pageNo = 1;
        this.refresh();
      },

      openCreate() {
        this.isEdit = false;
        this.form = defaultForm();
        this.dialogVisible = true;
      },

      openEdit(row) {
        this.isEdit = true;
        this.form = { ...defaultForm(), ...row };
        this.dialogVisible = true;
      },

      async submit() {
        const missing = formFields
          .filter((f) => f.required && (this.form[f.prop] === '' || this.form[f.prop] == null))
          .map((f) => f.label);
        if (missing.length) {
          ElMessage.warning(`请填写：${missing.join('、')}`);
          return;
        }
        this.saving = true;
        try {
          const payload = { ...this.form };
          // 编辑时保持主键存在，避免后端按主键更新失败
          if (this.isEdit && payload[idKey] == null) {
            ElMessage.error('缺少主键，无法更新');
            return;
          }
          if (this.isEdit) {
            await api.update(payload);
          } else {
            delete payload[idKey];
            await api.create(payload);
          }
          ElMessage.success(this.isEdit ? '修改成功' : '新增成功');
          this.dialogVisible = false;
          await this.refresh();
        } catch (e) {
          ElMessage.error(`保存失败：${e.message}`);
        } finally {
          this.saving = false;
        }
      },

      async confirmDelete(row) {
        const label = row.name || row[columns[0].prop] || '';
        try {
          await ElMessageBox.confirm(
            `确定要删除「${label}」吗？该操作不可撤销。`,
            '删除确认',
            { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
          );
        } catch (_) {
          return; // 用户取消
        }
        try {
          await api.remove(row[idKey]);
          ElMessage.success('已删除');
          // 删掉当前页最后一条时回退一页，避免停在空页
          if (this.rows.length === 1 && this.pageNo > 1) this.pageNo -= 1;
          await this.refresh();
        } catch (e) {
          ElMessage.error(`删除失败：${e.message}`);
        }
      },
    },
    template: `
      <div>
        <div class="page-head">
          <div>
            <h1>{{ title }}</h1>
            <p>{{ subtitle }}</p>
          </div>
          <div class="page-head-actions">
            <el-button @click="refresh"><app-icon name="refresh" :size="15" /> 刷新</el-button>
            <el-button type="primary" @click="openCreate">
              <app-icon name="plus" :size="15" /> {{ createLabel }}
            </el-button>
          </div>
        </div>

        <div class="card">
          <div class="toolbar">
            <el-input
              v-model="keyWord"
              :placeholder="searchPlaceholder"
              clearable
              style="width:262px"
              @keyup.enter="onSearch"
              @clear="onReset" />
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
            <div class="spacer"></div>
            <span class="muted" style="font-size:12.5px">共 {{ total }} 条</span>
          </div>

          <el-table :data="rows" v-loading="loading" style="width:100%" empty-text="暂无数据">
            <el-table-column v-for="c in columns" :key="c.prop"
              :prop="c.prop" :label="c.label" :width="c.width"
              :min-width="c.minWidth || 110" :align="c.align || 'left'"
              show-overflow-tooltip>
              <template #default="scope">
                <span v-if="!c.type">{{ c.format ? c.format(scope.row) : scope.row[c.prop] }}</span>
                <span v-else-if="c.type === 'chip'" class="chip" :class="c.chipClass ? c.chipClass(scope.row) : ''">
                  {{ c.format ? c.format(scope.row) : scope.row[c.prop] }}
                </span>
                <span v-else-if="c.type === 'mono'" class="mono">{{ c.format ? c.format(scope.row) : scope.row[c.prop] }}</span>
                <span v-else-if="c.type === 'muted'" class="muted">{{ c.format ? c.format(scope.row) : scope.row[c.prop] }}</span>
                <span v-else>{{ c.format ? c.format(scope.row) : scope.row[c.prop] }}</span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="140" align="right" fixed="right">
              <template #default="scope">
                <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
                <el-button link type="danger" @click="confirmDelete(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next, jumper"
              :total="total"
              :current-page="pageNo"
              :page-size="pageSize"
              :page-sizes="[10, 20, 50]"
              @current-change="onPageChange"
              @size-change="onSizeChange" />
          </div>
        </div>

        <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
          <el-form label-width="104px" label-position="right">
            <el-form-item v-for="f in formFields" :key="f.prop" :label="f.label" :required="!!f.required">
              <el-input v-if="!f.type || f.type === 'input'"
                v-model="form[f.prop]" :placeholder="f.placeholder || ''" clearable />
              <el-input v-else-if="f.type === 'textarea'"
                v-model="form[f.prop]" type="textarea" :rows="3" :placeholder="f.placeholder || ''" />
              <el-input-number v-else-if="f.type === 'number'"
                v-model="form[f.prop]" :min="f.min" :max="f.max" :step="f.step || 1" style="width:100%" />
              <el-select v-else-if="f.type === 'select'"
                v-model="form[f.prop]" style="width:100%" :placeholder="f.placeholder || '请选择'" clearable>
                <el-option v-for="o in f.options" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-date-picker v-else-if="f.type === 'date'"
                v-model="form[f.prop]" type="date" value-format="YYYY-MM-DD"
                style="width:100%" :placeholder="f.placeholder || '选择日期'" />
              <el-date-picker v-else-if="f.type === 'datetime'"
                v-model="form[f.prop]" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                style="width:100%" :placeholder="f.placeholder || '选择时间'" />
              <span v-if="f.hint" class="muted" style="font-size:12px">{{ f.hint }}</span>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
          </template>
        </el-dialog>
      </div>
    `,
  };
}

export default createCrudPage;
