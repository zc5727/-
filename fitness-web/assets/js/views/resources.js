/**
 * 业务资源页配置：会员 / 课程 / 器材 / 员工 / 报名。
 * 每页只声明列与表单字段，交互结构交给 createCrudPage 统一生成。
 */
import { createCrudPage } from './crud.js';
import { gymApi } from '../api.js';

const GENDER = [
  { value: 'M', label: '男' },
  { value: 'F', label: '女' },
];

const JOB = [
  { value: '1', label: '教练' },
  { value: '2', label: '前台' },
  { value: '3', label: '销售' },
  { value: '4', label: '经理' },
  { value: '5', label: '后勤' },
];

const EQUIP_STATE = [
  { value: 1, label: '正常' },
  { value: 2, label: '维修中' },
  { value: 0, label: '停用' },
];

const genderText = (v) => (v === 'M' ? '男' : v === 'F' ? '女' : v || '-');
const jobText = (v) => (JOB.find((j) => j.value === String(v)) || {}).label || v || '-';
const equipStateText = (v) => (EQUIP_STATE.find((s) => s.value === Number(v)) || {}).label || v || '-';
const dateText = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-');

/* ============================================================
   会员管理
   ============================================================ */
export const MemberPage = createCrudPage({
  title: '会员',
  subtitle: '会员档案的录入、检索与维护',
  idKey: 'memberNo',
  searchPlaceholder: '按姓名或手机号搜索',
  columns: [
    { prop: 'memberNo', label: '编号', width: 78, type: 'mono' },
    { prop: 'memberName', label: '姓名', minWidth: 110 },
    {
      prop: 'memberGender', label: '性别', width: 72, type: 'chip',
      chipClass: (r) => (r.memberGender === 'M' ? 'brand' : 'cyan'),
      format: (r) => genderText(r.memberGender),
    },
    { prop: 'memberAge', label: '年龄', width: 70 },
    { prop: 'memberPhone', label: '手机号', minWidth: 132, type: 'mono' },
    { prop: 'memberHeight', label: '身高', width: 88, format: (r) => (r.memberHeight != null ? `${r.memberHeight} cm` : '-') },
    { prop: 'memberWeight', label: '体重', width: 88, format: (r) => (r.memberWeight != null ? `${r.memberWeight} kg` : '-') },
    { prop: 'cardClass', label: '卡等级', width: 88 },
    { prop: 'memberIntegral', label: '积分', width: 86 },
    { prop: 'memberChange', label: '余额', width: 92, format: (r) => (r.memberChange != null ? `¥${r.memberChange}` : '-') },
    { prop: 'cardTime', label: '开卡时间', minWidth: 148, type: 'muted', format: (r) => dateText(r.cardTime) },
  ],
  formFields: [
    { prop: 'memberUsername', label: '登录账号', placeholder: '会员登录用账号' },
    { prop: 'memberPassword', label: '登录密码', placeholder: '不修改可留空' },
    { prop: 'memberName', label: '姓名', required: true },
    { prop: 'memberGender', label: '性别', type: 'select', options: GENDER, required: true },
    { prop: 'memberAge', label: '年龄', type: 'number', min: 10, max: 100 },
    { prop: 'memberPhone', label: '手机号', required: true, placeholder: '11 位手机号' },
    { prop: 'memberHeight', label: '身高(cm)', type: 'number', min: 100, max: 250, step: 0.5 },
    { prop: 'memberWeight', label: '体重(kg)', type: 'number', min: 25, max: 300, step: 0.5 },
    { prop: 'cardClass', label: '卡等级', type: 'number', min: 0 },
    { prop: 'cardNextClass', label: '下一等级', type: 'number', min: 0 },
    { prop: 'memberIntegral', label: '积分', type: 'number', min: 0 },
    { prop: 'memberChange', label: '账户余额', type: 'number', min: 0, step: 0.01 },
    { prop: 'cardTime', label: '开卡时间', type: 'datetime' },
    { prop: 'personalizedSignature', label: '个性签名', type: 'textarea' },
  ],
  api: {
    list: gymApi.memberList,
    search: gymApi.memberSearch,
    total: gymApi.memberTotal,
    totalFuzzy: gymApi.memberTotalFuzzy,
    create: gymApi.memberCreate,
    update: gymApi.memberUpdate,
    remove: gymApi.memberDelete,
  },
  defaultForm: () => ({
    memberUsername: '', memberPassword: '', memberName: '', memberGender: 'M',
    memberAge: 25, memberPhone: '', memberHeight: 170, memberWeight: 65,
    cardClass: 0, cardNextClass: 0, memberIntegral: 0, memberChange: 0,
    cardTime: '', personalizedSignature: '',
  }),
});

/* ============================================================
   课程管理（增改走 JSON）
   ============================================================ */
export const CoursePage = createCrudPage({
  title: '课程',
  subtitle: '课程定价、时长与授课教练编排',
  idKey: 'courseNo',
  searchPlaceholder: '按课程名搜索',
  columns: [
    { prop: 'courseNo', label: '编号', width: 78, type: 'mono' },
    { prop: 'courseName', label: '课程名称', minWidth: 140 },
    { prop: 'courseDuration', label: '时长', width: 92, format: (r) => (r.courseDuration ? `${r.courseDuration} 天` : '-') },
    { prop: 'coursePrice', label: '价格', width: 100, format: (r) => (r.coursePrice != null ? `¥${r.coursePrice}` : '-') },
    { prop: 'courseIntegral', label: '赠送积分', width: 96 },
    { prop: 'employeeNameCoach', label: '授课教练', minWidth: 110 },
    { prop: 'employeeNameManager', label: '负责经理', minWidth: 110 },
    { prop: 'courseTime', label: '开课时间', minWidth: 150, type: 'muted', format: (r) => dateText(r.courseTime) },
    { prop: 'courseDesc', label: '课程描述', minWidth: 170, type: 'muted' },
  ],
  formFields: [
    { prop: 'courseName', label: '课程名称', required: true },
    { prop: 'courseTime', label: '开课时间', type: 'date', required: true },
    { prop: 'courseDuration', label: '课程时长', placeholder: '如 30（天）' },
    { prop: 'coursePrice', label: '课程价格', type: 'number', min: 0, step: 1 },
    { prop: 'courseIntegral', label: '赠送积分', type: 'number', min: 0 },
    { prop: 'employeeNo', label: '授课教练', type: 'select', options: [], placeholder: '加载中…' },
    { prop: 'managerNo', label: '负责经理', type: 'select', options: [], placeholder: '加载中…' },
    { prop: 'courseDesc', label: '课程描述', type: 'textarea' },
  ],
  api: {
    list: gymApi.courseList,
    search: gymApi.courseSearch,
    total: gymApi.courseTotal,
    totalFuzzy: gymApi.courseTotalFuzzy,
    create: gymApi.courseCreate,
    update: gymApi.courseUpdate,
    remove: gymApi.courseDelete,
  },
  defaultForm: () => ({
    courseName: '', courseTime: '', courseDuration: '', coursePrice: 0,
    courseIntegral: 0, employeeNo: null, managerNo: null, courseDesc: '',
  }),
});

/* 课程页：挂载后把教练 / 经理下拉的选项填上 */
const courseOriginalMounted = CoursePage.mounted;
CoursePage.mounted = function () {
  courseOriginalMounted.call(this);
  (async () => {
    try {
      const [coaches, managers] = await Promise.all([gymApi.coachList(), gymApi.managerList()]);
      const f1 = this.config.formFields.find((f) => f.prop === 'employeeNo');
      const f2 = this.config.formFields.find((f) => f.prop === 'managerNo');
      if (f1) f1.options = (coaches || []).map((c) => ({ value: c.employeeNo, label: c.employeeName }));
      if (f2) f2.options = (managers || []).map((m) => ({ value: m.managerNo, label: m.employeeName }));
    } catch (_) { /* 下拉失败不阻塞页面主体 */ }
  })();
};

/* ============================================================
   器材管理
   ============================================================ */
export const EquipmentPage = createCrudPage({
  title: '器材',
  subtitle: '健身房器材台账与状态维护',
  idKey: 'equipmentNo',
  searchPlaceholder: '按器材名称搜索',
  columns: [
    { prop: 'equipmentNo', label: '编号', width: 78, type: 'mono' },
    { prop: 'equipmentName', label: '器材名称', minWidth: 140 },
    { prop: 'equipmentLocation', label: '摆放位置', minWidth: 120 },
    {
      prop: 'equipmentState', label: '状态', width: 100, type: 'chip',
      format: (r) => equipStateText(r.equipmentState),
      chipClass: (r) => (Number(r.equipmentState) === 1 ? 'success' : Number(r.equipmentState) === 2 ? 'warn' : 'danger'),
    },
    { prop: 'equipmentMessage', label: '备注', minWidth: 170, type: 'muted' },
  ],
  formFields: [
    { prop: 'equipmentName', label: '器材名称', required: true },
    { prop: 'equipmentLocation', label: '摆放位置', required: true },
    { prop: 'equipmentState', label: '状态', type: 'select', options: EQUIP_STATE, required: true },
    { prop: 'equipmentMessage', label: '备注', type: 'textarea' },
  ],
  api: {
    list: gymApi.equipmentList,
    search: gymApi.equipmentSearch,
    total: gymApi.equipmentTotal,
    totalFuzzy: gymApi.equipmentTotalFuzzy,
    create: gymApi.equipmentCreate,
    update: gymApi.equipmentUpdate,
    remove: gymApi.equipmentDelete,
  },
  defaultForm: () => ({ equipmentName: '', equipmentLocation: '', equipmentState: 1, equipmentMessage: '' }),
});

/* ============================================================
   员工管理
   ============================================================ */
export const EmployeePage = createCrudPage({
  title: '员工',
  subtitle: '教练与运营团队档案',
  idKey: 'employeeNo',
  searchPlaceholder: '按姓名或手机号搜索',
  columns: [
    { prop: 'employeeNo', label: '编号', width: 78, type: 'mono' },
    { prop: 'employeeName', label: '姓名', minWidth: 110 },
    {
      prop: 'employeeGender', label: '性别', width: 72, type: 'chip',
      chipClass: (r) => (r.employeeGender === 'M' ? 'brand' : 'cyan'),
      format: (r) => genderText(r.employeeGender),
    },
    { prop: 'employeeAge', label: '年龄', width: 70 },
    { prop: 'employeePhone', label: '手机号', minWidth: 132, type: 'mono' },
    { prop: 'employeeJob', label: '岗位', width: 96, type: 'chip', chipClass: () => 'brand', format: (r) => jobText(r.employeeJob) },
    { prop: 'employeeMessage', label: '备注', minWidth: 150, type: 'muted' },
    { prop: 'employeeTime', label: '入职时间', minWidth: 150, type: 'muted', format: (r) => dateText(r.employeeTime) },
  ],
  formFields: [
    { prop: 'employeeName', label: '姓名', required: true },
    { prop: 'employeeGender', label: '性别', type: 'select', options: GENDER, required: true },
    { prop: 'employeeAge', label: '年龄', type: 'number', min: 16, max: 70 },
    { prop: 'employeePhone', label: '手机号', required: true },
    { prop: 'employeeJob', label: '岗位', type: 'select', options: JOB, required: true },
    { prop: 'employeeMessage', label: '备注', type: 'textarea' },
  ],
  api: {
    list: gymApi.employeeList,
    search: gymApi.employeeSearch,
    total: gymApi.employeeTotal,
    totalFuzzy: gymApi.employeeTotalFuzzy,
    create: gymApi.employeeCreate,
    update: gymApi.employeeUpdate,
    remove: gymApi.employeeDelete,
  },
  defaultForm: () => ({ employeeName: '', employeeGender: 'M', employeeAge: 30, employeePhone: '', employeeJob: '1', employeeMessage: '' }),
});

/* ============================================================
   报名管理
   注意：register 表实际只有 registerNo / courseNo / memberNo 三列，
   课程名、会员名、教练信息均由后端 JOIN 得出，因此新增只需两个外键。
   ============================================================ */
export const RegisterPage = createCrudPage({
  title: '课程报名',
  subtitle: '会员报名记录的登记与维护',
  idKey: 'registerNo',
  searchPlaceholder: '按课程或会员名搜索',
  columns: [
    { prop: 'registerNo', label: '编号', width: 78, type: 'mono' },
    { prop: 'courseName', label: '课程名称', minWidth: 140 },
    { prop: 'memberName', label: '会员', minWidth: 110 },
    { prop: 'memberPhone', label: '会员手机号', minWidth: 136, type: 'mono' },
    { prop: 'employeeName', label: '负责教练', minWidth: 110 },
    { prop: 'employeePhone', label: '教练电话', minWidth: 136, type: 'mono' },
    { prop: 'courseDuration', label: '课时', width: 90, format: (r) => (r.courseDuration ? `${r.courseDuration} 节` : '-') },
    { prop: 'courseTime', label: '开课日期', minWidth: 122, type: 'muted' },
  ],
  formFields: [
    { prop: 'courseNo', label: '课程编号', type: 'number', required: true, min: 1, hint: '填写要报名的课程编号' },
    { prop: 'memberNo', label: '会员编号', type: 'number', required: true, min: 1, hint: '填写报名会员的编号' },
  ],
  api: {
    list: gymApi.registerList,
    search: gymApi.registerSearch,
    total: gymApi.registerTotal,
    totalFuzzy: gymApi.registerTotalFuzzy,
    create: gymApi.registerCreate,
    update: gymApi.registerUpdate,
    remove: gymApi.registerDelete,
  },
  defaultForm: () => ({ courseNo: 1, memberNo: 1 }),
});
