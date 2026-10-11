<template>

  <div class="home">
    <el-card shadow="hover">
      <div class="header">
        <div class="page-header">
          <el-page-header @back="goBack" content="课程管理" style="margin-bottom: 20px">
          </el-page-header>
        </div>

        <div class="buttons">
          <el-button type="primary" size="mini" @click="addCoursePage">新增</el-button>
<!--          <el-button type="primary" size="mini">导入</el-button>
          <el-button type="primary" size="mini">导出</el-button>-->
          <!--搜索区域-->
          <div class="search">
            <el-input placeholder="请输入内容" v-model="KeywordCourse" class="input-with-select" size="mini">
              <el-button slot="append" icon="el-icon-search" size="mini"
                         @click="getByKeyword(KeywordCourse)"></el-button>
            </el-input>
          </div>
        </div>

      </div>


      <el-table
          :data="tableData"
          border
          stripe
          size="mini"
          style="width: 100%;">
        <el-table-column
            type="index"
            :index="indexMethod"
            label="序号"
            width="60"
            style="height:50px"
        >
        </el-table-column>
        <el-table-column
            prop="courseName"
            label="课程名称">
        </el-table-column>
        <el-table-column
            prop="courseTime"
            label="开课时间"
            width="150">
          <template slot-scope="scope">
            <span>{{scope.row.courseTime | dataFormat}}</span>
          </template>
        </el-table-column>
        <el-table-column
            prop="courseDuration"
            label="课程时长">
        </el-table-column>
        <el-table-column
            prop="employeeNameCoach"
            label="教练">
        </el-table-column>
        <el-table-column
            prop="employeePhoneCoach"
            label="教练电话">
        </el-table-column>
        <el-table-column
            prop="employeeNameManager"
            label="经理">
        </el-table-column>
        <el-table-column
            prop="employeePhoneManager"
            label="经理电话">
        </el-table-column>
        <el-table-column
            prop="courseIntegral"
            label="课程积分">
        </el-table-column>
        <el-table-column
            prop="coursePrice"
            label="课程售价">
        </el-table-column>
        <el-table-column
            prop="courseDesc"
            label="课程描述">
        </el-table-column>

        <el-table-column
            fixed="right"
            label="操作">
          <template slot-scope="scope">
            <el-button @click="editCourse(scope.row)" type="text" size="mini">编辑</el-button>
            <el-button slot="reference" type="text" size="mini" @click="deleteCourse(scope.row.courseNo)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex;justify-content: center;margin-top: 10px">
        <el-pagination
            background
            layout="prev, pager, next"
            @current-change="changePage"
            :total="total">
        </el-pagination>
      </div>


      <el-dialog
          title="添加课程"
          :visible.sync="dialogVisible2"
          width="30%"
      >
        <el-form ref="form" :model="sizeForm" label-width="80px" size="mini">
          <el-form-item label="课程名称">
            <el-input v-model="sizeForm.courseName"></el-input>
          </el-form-item>
          <el-form-item label="开课时间">
            <el-date-picker
                v-model="sizeForm.courseTime"
                type="datetime"
                placeholder="选择日期时间"
                align="right"
                :picker-options="pickerOptions">
            </el-date-picker>
          </el-form-item>
          <el-form-item label="课程时长">
            <el-input v-model="sizeForm.courseDuration"></el-input>
          </el-form-item>
          <!-- <el-form-item label="教练">
            <el-input v-model="sizeForm.employeeNo"></el-input>
          </el-form-item>
          <el-form-item label="项目经理">
            <el-input v-model="sizeForm.managerNo"></el-input>
          </el-form-item> -->
          <el-form-item label="教练" prop="employeeNo">
            <el-select v-model="sizeForm.employeeNo" filterable placeholder="请选择">
              <el-option
                  v-for="item in coachList"
                  :key="item.employeeNo"
                  :label="item.employeeName"
                  :value="item.employeeNo">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="项目经理" prop="managerNo">
            <el-select v-model="sizeForm.managerNo" filterable placeholder="请选择">
              <el-option
                  v-for="item in managerList"
                  :key="item.managerNo"
                  :label="item.employeeName"
                  :value="item.managerNo">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="价格">
            <el-input v-model="sizeForm.coursePrice"></el-input>
          </el-form-item>
          <el-form-item label="积分">
            <el-input v-model="sizeForm.courseIntegral"></el-input>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="sizeForm.courseDesc"></el-input>
          </el-form-item>
          <el-form-item label="照片">
            <el-upload
              class="upload-demo"
              action="#"
              :show-file-list="false"
              :auto-upload="false"
              list-type="picture-card">
              <i class="el-icon-plus"></i>
            </el-upload>
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="dialogVisible2 = false">取 消</el-button>
          <el-button type="primary" @click="addCourse">确 定</el-button>
        </span>
      </el-dialog>

      <el-dialog
          title="修改课程信息"
          :visible.sync="dialogVisible"
          width="30%"
      >
        <el-form ref="form" :model="sizeForm" label-width="80px" size="mini">
          <el-form-item label="课程名称">
            <el-input v-model="sizeForm.courseName"></el-input>
          </el-form-item>
          <el-form-item label="开课时间">
            <el-date-picker
                v-model="sizeForm.courseTime"
                type="datetime"
                placeholder="选择日期时间"
                align="right"
                :picker-options="pickerOptions">
            </el-date-picker>
          </el-form-item>
          <el-form-item label="课程时长">
            <el-input v-model="sizeForm.courseDuration"></el-input>
          </el-form-item>
          <el-form-item label="教练" prop="employeeNo">
            <el-select v-model="sizeForm.employeeNo" filterable placeholder="请选择">
              <el-option
                  v-for="item in coachList"
                  :key="item.employeeNo"
                  :label="item.employeeName"
                  :value="item.employeeNo">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="项目经理" prop="managerNo">
            <el-select v-model="sizeForm.managerNo" filterable placeholder="请选择">
              <el-option
                  v-for="item in managerList"
                  :key="item.managerNo"
                  :label="item.employeeName"
                  :value="item.managerNo">
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="价格">
            <el-input v-model="sizeForm.coursePrice"></el-input>
          </el-form-item>
          <el-form-item label="积分">
            <el-input v-model="sizeForm.courseIntegral"></el-input>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="sizeForm.courseDesc"></el-input>
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="updateCourse">确 定</el-button>
        </span>
      </el-dialog>
    </el-card>

  </div>

</template>

<script>
import moment from 'moment';
import {
  addCourse,
  deleteCourse,
  getAllCourse,
  getByKeywordCourse,
  totalCourse,
  getAllManager,
  getAllCoach,
  totalCourseFuzzy,
  updateCourse
} from "@/api/allApi";

export default {
  name: "CourseManage",
  data() {
    return {
      dialogFormVisible: false,
      tableData: [],
      sizeForm: {},
      coachList:[],
      managerList:[],
      dialogVisible: false,
      dialogVisible2: false,
      /*分页*/
      pageSize: 10,
      currentPage: 1,
      total: 0,
      searchValue: '',
      KeywordCourse: '',
      pickerOptions: {
        shortcuts: [{
          text: '今天',
          onClick(picker) {
            picker.$emit('pick', new Date());
          }
        }, {
          text: '昨天',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000 * 24);
            picker.$emit('pick', date);
          }
        }, {
          text: '一周前',
          onClick(picker) {
            const date = new Date();
            date.setTime(date.getTime() - 3600 * 1000 * 24 * 7);
            picker.$emit('pick', date);
          }
        }]
      },
    }
  },
  computed: {
    indexMethod() {
      return (this.currentPage - 1) * 10 + 1
    }
  },
  filters: {
    dataFormat(value) {
      return moment(value).format("YYYY-MM-DD HH:mm:ss")
    }
  },
  mounted() {
    getAllCourse({
      page: 0,
      size: 10
    }).then(res => {
      this.tableData = res.data
    }).catch(err => {
      console.log(err.message)
    })

    totalCourse().then(res => {
      this.total = res.data.dataTotal
    })

    this.getAllCoach()
    this.getAllManager()
  },
  methods: {
    onSubmit() {
      this.dialogFormVisible = false
      this.clear()
    },
    clear() {
      this.form = {
        username: '',
        password: '',
        name: '',
        gender: '',
        phone: '',
      }
    },
    cancel() {
      this.dialogFormVisible = false,
          this.form = {
            username: '',
            password: '',
            name: '',
            gender: '',
            phone: '',
          }
    },
    goBack() {
      this.$router.back();
    },
    handleClose(done) {
      this.$confirm('确认关闭？')
          .then(_ => {
            done();
          })
          .catch(_ => {
          });
    },
    editCourse(value) {
      this.sizeForm = value
      this.dialogVisible = true
    },
    addCoursePage() {
      this.dialogVisible2 = true
      this.sizeForm = {}
    },
    addCourse() {
      let currentPage = this.currentPage
      let pageSize = this.pageSize
      addCourse({
        courseName: this.sizeForm.courseName,
        courseTime: this.sizeForm.courseTime,
        courseDuration: this.sizeForm.courseDuration,
        employeeNo: this.sizeForm.employeeNo,
        managerNo:this.sizeForm.managerNo,
        courseIntegral:this.sizeForm.courseIntegral,
        coursePrice:this.sizeForm.coursePrice,
        courseDesc:this.sizeForm.courseDesc
      }).then(res => {
        console.log(res.data);
        if (res.data.code === 200) {
          //刷新表格
          getAllCourse({
            page: currentPage,
            size: pageSize
          }).then(res => {
            this.tableData = res.data
          })
          alert(res.data.message)
        } else {
          alert(res.data.message)
        }
      }).catch(err => {
        console.log(err.message)
      })
      this.sizeForm = {}, this.dialogVisible2 = false

    },

    deleteCourse(courseNo) {
      this.$confirm('此操作将永久删除该信息, 是否继续?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        //确定 发送ajax请求
        deleteCourse({courseNo: courseNo}).then(res => {
          if (res.data.code === 200) {
            //刷新表格
            getAllCourse({
              page: 0,
              size: 10
            }).then(res => {
              this.tableData = res.data
            })
          } else {
            alert(res.data.message)
          }
        }).catch(err => {
          console.log(err.message)
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消删除'
        });
      });
    },
    updateCourse() {
      updateCourse(this.sizeForm).then(res => {
        if (res.data.code === 200) {
          //刷新表格
          getAllCourse({
            page: 0,
            size: 10
          }).then(res => {
            this.tableData = res.data
          })
        } else {
          alert(res.data.message)
        }
      }).catch(err => {
        console.log(err.message)
      })
      this.dialogVisible = false
    },
    changePage(params) {
      this.currentPage = (params - 1) * this.pageSize
      this.getAllCourse()
      this.currentPage = params
    },
    getByKeyword(KeywordCourse) {
      totalCourseFuzzy({
        keyWord: KeywordCourse
      }).then(res => {
        this.total = res.data.dataTotal
      }),
          getByKeywordCourse({
            keyWord: KeywordCourse,
            page: 0,
            size: 10
          }).then(res => {
            this.tableData = res.data
          }).catch(err => {
            console.log(err.message)
          })

    },
    // 获取所有教练
    getAllCoach() {
      getAllCoach().then(res=>{
        this.coachList = res.data
      })
    },
    getAllManager() {
      getAllManager().then(res=>{
        this.managerList = res.data
      })
    },
    //获取数据方法
    getAllCourse() {
      getAllCourse({
        page: this.currentPage,
        size: this.pageSize
      }).then(res => {
        this.tableData = res.data
      }).catch(err => {
        console.log(err.message)
      })
    }
  },
}
</script>


<style scoped>
.header {
  display: flex;
  justify-content: space-between;
}

.page-header {
  width: 20%;
}

.buttons {
  margin: 0px 0 10px 0;

}

.search {
  margin-right: 10px;
  width: 50%;
  float: left;
}

.search-input input {
  width: 110px;
}

.upload-demo ::v-deep .el-upload--picture-card {
  width: 80px;
  height: 80px;
  line-height: 80px;
}
.upload-demo ::v-deep .el-upload-list--picture-card .el-upload-list__item {
  width: 80px;
  height: 80px;
}
</style>
