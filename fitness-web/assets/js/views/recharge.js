/**
 * 充值管理。
 * 业务系统只提供「按会员编号查询充值记录」与「新增充值」两个接口，
 * 没有全量列表接口，因此页面结构是：先定位会员 → 再看记录 → 再登记充值。
 */
import { gymApi } from '../api.js';

const { ElMessage } = ElementPlus;

const METHODS = ['在线充值', '现金', '微信', '支付宝', '银行卡'];

export default {
  name: 'RechargePage',
  data() {
    return {
      memberNo: null,
      loading: false,
      records: [],
      submitting: false,
      dialogVisible: false,
      form: { rechargeDate: '', rechargeMethod: '在线充值', rechargeStatus: 1, rechargeMoney: 100 },
      methods: METHODS,
    };
  },
  computed: {
    totalAmount() {
      return this.records.reduce((s, r) => s + Number(r.rechargeMoney || 0), 0);
    },
    successCount() {
      return this.records.filter((r) => Number(r.rechargeStatus) === 1).length;
    },
  },
  methods: {
    async query() {
      if (!this.memberNo) {
        ElMessage.warning('请输入会员编号');
        return;
      }
      this.loading = true;
      try {
        const data = await gymApi.rechargeByMember(this.memberNo);
        this.records = Array.isArray(data) ? data : [];
      } catch (e) {
        this.records = [];
        ElMessage.error(`查询失败：${e.message}`);
      } finally {
        this.loading = false;
      }
    },

    reset() {
      this.memberNo = null;
      this.records = [];
    },

    openCreate() {
      if (!this.memberNo) {
        ElMessage.warning('请先输入会员编号并查询');
        return;
      }
      const today = new Date();
      const iso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
      this.form = { rechargeDate: iso, rechargeMethod: '在线充值', rechargeStatus: 1, rechargeMoney: 100 };
      this.dialogVisible = true;
    },

    async submit() {
      if (!this.form.rechargeMoney || this.form.rechargeMoney <= 0) {
        ElMessage.warning('充值金额需大于 0');
        return;
      }
      this.submitting = true;
      try {
        await gymApi.rechargeCreate({ ...this.form, memberNo: this.memberNo });
        ElMessage.success('充值登记成功');
        this.dialogVisible = false;
        await this.query();
      } catch (e) {
        ElMessage.error(`登记失败：${e.message}`);
      } finally {
        this.submitting = false;
      }
    },

    statusText: (v) => (Number(v) === 1 ? '成功' : '未完成'),
  },
  template: `
    <div>
      <div class="page-head">
        <div>
          <h1>充值管理</h1>
          <p>按会员查询充值流水，并登记新的充值记录</p>
        </div>
        <div class="page-head-actions">
          <el-button @click="reset">重置</el-button>
          <el-button type="primary" @click="openCreate">
            <app-icon name="plus" :size="15" /> 登记充值
          </el-button>
        </div>
      </div>

      <div class="card">
        <div class="toolbar">
          <el-input-number v-model="memberNo" :min="1" placeholder="会员编号"
            style="width:170px" />
          <el-button type="primary" @click="query">
            <app-icon name="search" :size="15" /> 查询流水
          </el-button>
          <div class="spacer"></div>
          <span class="muted" style="font-size:12.5px">
            共 {{ records.length }} 笔 · 成功 {{ successCount }} 笔 · 合计 ¥{{ totalAmount.toFixed(2) }}
          </span>
        </div>

        <el-table :data="records" v-loading="loading" style="width:100%"
          empty-text="输入会员编号后点击「查询流水」">
          <el-table-column prop="rechargeNo" label="流水号" width="90" />
          <el-table-column prop="rechargeDate" label="充值日期" min-width="130" />
          <el-table-column prop="rechargeMethod" label="充值方式" min-width="120">
            <template #default="scope">
              <span class="chip brand">{{ scope.row.rechargeMethod }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="rechargeMoney" label="金额" min-width="110" align="right">
            <template #default="scope">
              <span style="font-weight:700;color:#4f46e5">¥{{ Number(scope.row.rechargeMoney).toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="rechargeStatus" label="状态" width="100">
            <template #default="scope">
              <span class="chip" :class="Number(scope.row.rechargeStatus) === 1 ? 'success' : 'warn'">
                {{ Number(scope.row.rechargeStatus) === 1 ? '成功' : '未完成' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="memberNo" label="会员编号" width="100" />
        </el-table>
      </div>

      <el-dialog v-model="dialogVisible" title="登记充值" width="480px" destroy-on-close>
        <el-form label-width="92px">
          <el-form-item label="会员编号">
            <el-input :model-value="memberNo" disabled />
          </el-form-item>
          <el-form-item label="充值日期" required>
            <el-date-picker v-model="form.rechargeDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
          </el-form-item>
          <el-form-item label="充值方式" required>
            <el-select v-model="form.rechargeMethod" style="width:100%">
              <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="充值金额" required>
            <el-input-number v-model="form.rechargeMoney" :min="0.01" :step="10" :precision="2" style="width:100%" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.rechargeStatus">
              <el-radio :value="1">成功</el-radio>
              <el-radio :value="0">未完成</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submit">确认充值</el-button>
        </template>
      </el-dialog>
    </div>
  `,
};
