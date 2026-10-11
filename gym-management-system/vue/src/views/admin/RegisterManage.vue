<template>

  <div class="home">
    <el-card  shadow="hover">
      <div class="header">
        <div class="page-header">
          <el-page-header @back="goBack" content="报名表管理" style="margin-bottom: 20px">
          </el-page-header>
        </div>

        <div class="buttons">
          <el-button type="primary" size="mini" @click="addRegisterPage">新增</el-button>
<!--          <el-button type="primary" size="mini">导入</el-button>
          <el-button type="primary" size="mini">导出</el-button>-->
          <!--搜索区域-->
          <div class="search">
            <el-input placeholder="请输入关键字" v-model="KeywordRegister" class="input-with-select" size="mini" clearable @keyup.enter="getByKeyword(KeywordMember)">
              <el-button slot="append" icon="el-icon-search" size="mini" @click="getByKeyword(KeywordRegister)"></el-button>
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
            prop="courseName"
            label="课程名称">
        </el-table-column>
        <el-table-column
            prop="courseTime"
            label="开课时间">
        </el-table-column>
        <el-table-column
            prop="courseDuration"
            label="课程时长">
        </el-table-column>
        <el-table-column
            prop="employeeName"
            label="教练">
        </el-table-column>
        <el-table-column
            prop="employeePhone"
            label="教练手机号">
        </el-table-column>
        <el-table-column
            prop="memberName"
            label="会员">
        </el-table-column>
        <el-table-column
            prop="memberPhone"
            label="会员手机号">
        </el-table-column>
        <el-table-column
            fixed="right"
            label="操作">
          <template slot-scope="scope">
            <el-button slot="reference" type="text" size="mini"  @click="deleteRegister(scope.row.registerNo)">删除</el-button>
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
          title="添加报名"
          :visible.sync="dialogVisible2"
          width="30%"
      >
        <el-form ref="form" :model="sizeForm" label-width="120px" size="mini">
          <el-form-item label="课程名称" prop="region">
            <el-select v-model="sizeForm.courseNo" placeholder="请选择课程">
              <el-option v-for="item in courseList" :label="item.courseName" :value="item.courseNo"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="会员手机号" clearable >
            <el-input v-model="sizeForm.memberPhone" ></el-input>
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="dialogVisible2 = false">取 消</el-button>
          <el-button type="primary" @click="addRegister">确 定</el-button>
        </span>
      </el-dialog>
    </el-card>
  </div>

</template>

<script>
import moment from 'moment';
import {getAllCourseRegister, getAllRegister,addRegister,
  deleteRegister,updateRegister,totalRegister,
  getByKeywordRegister,totalRegisterFuzzy} from "@/api/allApi";


export default {
  name:"MemberManage",
  data() {
    return {
      dialogFormVisible: false,
      dialogVisible2:false,
      tableData: [],
      sizeForm:{

      },
      dialogVisible: false,
      /*分页*/
      pageSize:10,
      currentPage:1,
      total:0,
      searchValue:'',
      KeywordRegister:'',
      courseList:[]
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
    getAllRegister({
      page:0,
      size:10
    }).then(res=>{
      this.tableData = res.data
    }).catch(err=>{
      console.log(err.message)
    })

    totalRegister().then(res=>{
      this.total = res.data.dataTotal
    })


    getAllCourseRegister({
    }).then(res=>{
      this.courseList = res.data
    }).catch(err=>{
      console.log(err.message)
    })


  },
  methods: {
    onSubmit() {
      console.log('submit!')
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
    addRegisterPage(){

      this.dialogVisible2 = true

    },
    addRegister(){
      addRegister({
        courseNo:this.sizeForm.courseNo,
        memberPhone:this.sizeForm.memberPhone,
      }).then(res=>{
        if(res.data.code===200){
          //刷新表格
          getAllRegister({
            page:0,
            size:10
          }).then(res=>{
            this.tableData = res.data
          })
          alert(res.data.message)
        }else{
          alert(res.data.message)
        }
      }).catch(err=>{
        console.log(err.message)
      })
      this.sizeForm = {},
          this.dialogVisible2 = false

    },


    deleteRegister(registerNo){
      this.$confirm('此操作将永久删除该文件, 是否继续?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        //确定 发送ajax请求
        deleteRegister({registerNo:registerNo}).then(res=>{
          if(res.data.code===200){
            //刷新表格
            getAllRegister({
              page:0,
              size:10
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
    changePage(params) {
      this.currentPage = (params-1) * this.pageSize
      this.getAllRegister()
      this.currentPage = params
    },
    getByKeyword(KeywordRegister) {
      totalRegisterFuzzy({
        keyWord:KeywordRegister
      }).then(res=>{
        this.total = res.data.dataTotal
      }),
          getByKeywordRegister({
            keyWord:KeywordRegister,
            page:0,
            size:10
          }).then(res=>{
            this.tableData = res.data
          }).catch(err=>{
            console.log(err.message)
          })

    },
    //获取数据方法
    getAllRegister(){
      getAllRegister({
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
  width: 50%;
  float: left;
}


.search-input input{
  width: 110px;
}

</style>
