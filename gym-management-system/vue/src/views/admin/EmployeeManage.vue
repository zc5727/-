<template>

  <div class="home">
    <el-card  shadow="hover">
      <div class="header">
        <div class="page-header">
          <el-page-header @back="goBack" content="管理员工" style="margin-bottom: 20px">
          </el-page-header>
        </div>

        <div class="buttons">
<!--          <el-button type="primary" size="mini">导入</el-button>
          <el-button type="primary" size="mini">导出</el-button>-->
          <!--搜索区域-->
          <div class="search">
            <el-input placeholder="请输入内容" v-model="KeywordEmployee" class="input-with-select" size="mini">
              <el-button slot="append" icon="el-icon-search" size="mini" @click="getByKeyword(KeywordEmployee)"></el-button>
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
            style="height:50px"
        >
        </el-table-column>
        <el-table-column
            prop="employeeNo"
            label="员工工号"
        >
        </el-table-column>
        <el-table-column
            prop="employeeName"
            label="姓名">
        </el-table-column>
        <el-table-column
            prop="employeeGender"
            label="性别">
          <template slot-scope="scope">
            <span v-if="scope.row.employeeGender == 'F'">女</span>
            <span v-else>男</span>
          </template>
        </el-table-column>
        <el-table-column
            prop="employeeAge"
            label="年龄">
        </el-table-column>
        <el-table-column
            prop="employeePhone"
            label="手机号">
        </el-table-column>
        <el-table-column
            prop="employeeTime"
            label="入职时间">
          <template slot-scope="scope">
            <span>{{scope.row.employeeTime | dataFormat}}</span>
          </template>
        </el-table-column>
        <el-table-column
            prop="employeeJob"
            label="职务">
          <template slot-scope="scope">
            <span v-if="scope.row.employeeJob == '1'">教练</span>
            <span v-else-if="scope.row.employeeJob == '2'">前台</span>
            <span v-else-if="scope.row.employeeJob == '3'">保洁</span>
            <span v-else>经理</span>
          </template>
        </el-table-column>
        <el-table-column
            prop="employeeMessage"
            label="备注信息">
        </el-table-column>
        <el-table-column
            label="照片"
            width="100">
          <template slot-scope="scope">
            <img v-if="scope.$index < 6" :src="require(`@/assets/images/照片${scope.$index+1}.png`)" alt="员工照片" style="width:80px;height:80px;object-fit:cover;border-radius:8px;" />
            <img v-else src="https://via.placeholder.com/80x80?text=员工" alt="员工照片" style="width:80px;height:80px;object-fit:cover;border-radius:8px;" />
          </template>
        </el-table-column>
        <el-table-column
            fixed="right"
            label="操作">
          <template slot-scope="scope">
            <el-button @click="editEmployee(scope.row)" type="text" size="mini">编辑</el-button>
            <el-button slot="reference" type="text" size="mini"  @click="deleteEmployee(scope.row.employeeNo)">删除</el-button>
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
          title="修改员工信息"
          :visible.sync="dialogVisible"
          width="30%"
      >
        <el-form ref="form" :model="sizeForm" label-width="80px" size="mini">
          <el-form-item label="姓名">
            <el-input v-model="sizeForm.employeeName"></el-input>
          </el-form-item>
          <el-form-item label="年龄">
            <el-input v-model="sizeForm.employeeAge"></el-input>
          </el-form-item>
          <el-form-item label="性别">
            <el-select v-model="sizeForm.employeeGender" placeholder="请选择性别">
              <el-option label="男" value="M"></el-option>
              <el-option label="女" value="F"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="手机号">
            <el-input v-model="sizeForm.employeePhone"></el-input>
          </el-form-item>
          <el-form-item label="职务" prop="region">
            <el-select v-model="sizeForm.employeeJob" placeholder="请选择职务">
              <el-option label="教练" value="1"></el-option>
              <el-option label="经理" value="4"></el-option>
              <el-option label="前台" value="2"></el-option>
              <el-option label="保洁" value="3"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="备注信息">
            <el-input v-model="sizeForm.employeeMessage"></el-input>
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="updateEmployee">确 定</el-button>
        </span>
      </el-dialog>
    </el-card>

  </div>

</template>

<script>
import moment from 'moment';
import {
  deleteEmployee,
  getAllEmployee,
  getByKeywordEmployee,
  totalEmployee,
  totalEmployeeFuzzy,
  updateEmployee
} from "@/api/allApi";

export default {
  name:"EmployeeManage",
  data() {
    return {
      dialogFormVisible: false,
      tableData: [],
      sizeForm:{
      },
      dialogVisible: false,
      /*分页*/
      pageSize:10,
      currentPage:1,
      total:0,
      searchValue:'',
      KeywordEmployee:''
    }
  },
  computed: {
    indexMethod(){
      return (this.currentPage-1) *10 +1
    }
  },
  filters:{
    dataFormat(value){
      return moment(value).format("YYYY-MM-DD")
    }
  },
  mounted() {
    getAllEmployee({
      page:0,
      size:10
    }).then(res=>{
      this.tableData = res.data
    }).catch(err=>{
      console.log(err.message)
    })

    totalEmployee().then(res=>{
      this.total = res.data.dataTotal
    })
  },
  methods: {
    onSubmit() {
      this.dialogFormVisible = false
      this.clear()
    },
    clear() {
      this.form={
        username: '',
        password:'',
        name:'',
        gender:'',
        phone:'',
      }
    },
    cancel(){
      this.dialogFormVisible = false,
          this.form={
            username: '',
            password:'',
            name:'',
            gender:'',
            phone:'',
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
          .catch(_ => {});
    },
    editEmployee(value) {
      this.sizeForm = value
      this.sizeForm.employeeJob = value.employeeJob.toString()
      this.dialogVisible = true
    },

    deleteEmployee(employeeNo){
      let currentPage = this.currentPage
      let pageSize = this.pageSize
      this.$confirm('此操作将永久删除该信息, 是否继续?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        //确定 发送ajax请求
        deleteEmployee({employeeNo:employeeNo}).then(res=>{
          if(res.data.code===200){
            //刷新表格
            getAllEmployee({
              page:currentPage,
              size:pageSize
            }).then(res=>{
              this.tableData = res.data
            })
          }else{
            alert(res.data.message)
          }
        }).catch(err=>{
          console.log(err.message)
        })
      }).catch(() => {
        this.$message({
          type: 'info',
          message: '已取消删除'
        });
      });
    },
    updateEmployee() {
      let currentPage = this.currentPage
      let pageSize = this.pageSize
      updateEmployee({
        employeeName:this.sizeForm.employeeName,
        employeeAge:this.sizeForm.employeeAge,
        employeeGender:this.sizeForm.employeeGender,
        employeePhone:this.sizeForm.employeePhone,
        employeeJob:this.sizeForm.employeeJob,
        employeeMessage:this.sizeForm.employeeMessage,
        employeeNo:this.sizeForm.employeeNo
      }).then(res=>{
        if(res.data.code===200){
          //刷新表格
          getAllEmployee({
            page:currentPage,
            size:pageSize
          }).then(res=>{
            this.tableData = res.data
          })
        }else{
          alert(res.data.message)
        }
      }).catch(err=>{
        console.log(err.message)
      })
      this.dialogVisible = false
    },
    changePage(params) {
      this.currentPage = (params-1) * this.pageSize
      this.getAllEmployee()
      this.currentPage = params
    },
    getByKeyword(KeywordEmployee) {
      totalEmployeeFuzzy({
        keyWord:KeywordEmployee
      }).then(res=>{
        this.total = res.data.dataTotal
      }),
          getByKeywordEmployee({
            keyWord:KeywordEmployee,
            page:0,
            size:10
          }).then(res=>{
            this.tableData = res.data
          }).catch(err=>{
            console.log(err.message)
          })

    },
    //获取数据方法
    getAllEmployee(){
      getAllEmployee({
        page:this.currentPage,
        size:this.pageSize
      }).then(res=>{
        this.tableData = res.data
      }).catch(err=>{
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
  width: 60%;
  float: left;
}

.search-input input{
  width: 120px;
}

</style>
