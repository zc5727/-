<template>

  <div class="home">
    <el-card  shadow="hover">
      <div class="header">
        <div class="page-header">
          <el-page-header @back="goBack" content="管理用户" style="margin-bottom: 20px">
          </el-page-header>
        </div>

        <div class="buttons">
          <el-button type="primary" size="mini" @click="memberExport">导出</el-button>
          <el-button type="success" size="mini" @click="memberImportDialog = true">导入</el-button>
          <el-button type="danger" size="mini">批量删除</el-button>
          <!--搜索区域-->
            <div class="search">
              <el-input placeholder="请输入关键字" v-model="KeywordMember" class="input-with-select" size="mini" clearable @keyup.enter="getByKeyword(KeywordMember)">
                <el-button slot="append" icon="el-icon-search" size="mini" @click="getByKeyword(KeywordMember)"></el-button>
              </el-input>
            </div>
        </div>

      </div>



      <el-table

          :data="tableData"

          stripe
          size="mini"
          style="width: 100%;">
        <el-table-column
            type="selection"
            width="55">
        </el-table-column>
        <el-table-column
            type="index"
            :index="indexMethod"
            label="序号"
            style="height:50px">
        </el-table-column>
        <el-table-column
            prop="memberUsername"
            label="用户名"
        >
        </el-table-column>
        <el-table-column
            prop="memberName"
            label="姓名">
        </el-table-column>
        <el-table-column
            prop="memberAge"
            label="年龄">
        </el-table-column>
        <el-table-column
            prop="memberGender"
            label="性别">
          <template slot-scope="scope">
            <span v-if="scope.row.memberGender == 'F'">女</span>
            <span v-else>男</span>
          </template>
        </el-table-column>
        <el-table-column
            prop="memberPhone"
            label="手机号"
            width=120>
        </el-table-column>
        <el-table-column
            prop="memberHeight"
            label="身高">
        </el-table-column>
        <el-table-column
            prop="memberWeight"
            label="体重">
        </el-table-column>
        <el-table-column
            prop="cardTime"
            label="开卡时间">
          <template slot-scope="scope">
            <span>{{scope.row.cardTime | dataFormat}}</span>
          </template>
        </el-table-column>
        <el-table-column
            prop="cardClass"
            label="购买课时">
        </el-table-column>
        <el-table-column
            prop="memberIntegral"
            label="积分">
        </el-table-column>
        <el-table-column
            prop="memberChange"
            label="余额">
        </el-table-column>
        <el-table-column
            prop="memberPower"
            label="会员等级">
          <template slot-scope="scope">
            <span v-if="scope.row.memberPower == '0'"><el-tag  type="info" size="small" color="">普通用户</el-tag></span>
            <span v-else-if="scope.row.memberPower == '1'"><el-tag size="small" color="">体验VIP</el-tag></span>
            <span v-else-if="scope.row.memberPower == '2'"><el-tag type="success" size="small" color="">包月VIP</el-tag></span>
            <span v-else-if="scope.row.memberPower == '3'"><el-tag type="warning" size="small" color="">包季VIP</el-tag></span>
            <span v-else-if="scope.row.memberPower == '4'"><el-tag type="danger" size="small">包年VIP</el-tag></span>
            <span v-else>男</span>
          </template>
        </el-table-column>


        <el-table-column
            fixed="right"
            label="操作">
          <template slot-scope="scope">
            <el-button @click="editMember(scope.row)" type="text" size="mini">编辑</el-button>
            <el-button slot="reference" type="text" size="mini"  @click="deleteMember(scope.row.memberNo)">删除</el-button>
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
          title="导入Excel文件"
          :visible.sync="memberImportDialog"
          width="30%"
          :before-close="handleClose">

        <el-upload
            class="upload-demo"
            action
            drag
            :http-request="uploadFile"
            ref="upload"
            :on-exceed="handleExceed"
            :before-upload="beforeUpload"
            :show-file-list="true"
            :headers="headers"
            multiple>
          <i class="el-icon-upload"></i>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <div class="el-upload__tip" slot="tip">只能上传Excel文件</div>
        </el-upload>

        <span slot="footer" class="dialog-footer">
          <el-button @click="memberImportDialog = false">取 消</el-button>
          <el-button type="primary" @click="memberImport">确 定</el-button>
        </span>
      </el-dialog>


      <el-dialog
          title="修改用户信息"
          :visible.sync="dialogVisible"
          width="30%"
         >
        <el-form ref="form" :model="sizeForm" label-width="80px" size="mini" >
          <el-form-item label="姓名">
            <el-input v-model="sizeForm.memberName" clearable></el-input>
          </el-form-item>
          <el-form-item label="性别">
            <el-select v-model="sizeForm.memberGender" placeholder="请选择性别">
              <el-option label="男" value="M"></el-option>
              <el-option label="女" value="F"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="年龄">
            <el-input v-model="sizeForm.memberAge"></el-input>
          </el-form-item>
          <el-form-item label="手机号" clearable>
            <el-input v-model="sizeForm.memberPhone"></el-input>
          </el-form-item>
          <el-form-item label="积分" prop="memberIntegral">
            <el-input v-model="sizeForm.memberIntegral" clearable></el-input>
          </el-form-item>
          <el-form-item label="余额" prop="memberChange">
            <el-input v-model="sizeForm.memberChange" clearable></el-input>
          </el-form-item>
          <el-form-item label="会员等级" prop="memberPower">
            <el-select v-model="sizeForm.memberPower" placeholder="请选择会员等级" >
              <el-option label="普通用户" value="0"></el-option>
              <el-option label="体验vip" value="1"></el-option>
              <el-option label="包月vip" value="2"></el-option>
              <el-option label="包季vip" value="3"></el-option>
              <el-option label="包年vip" value="4"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="身高">
            <el-input v-model="sizeForm.memberHeight"></el-input>
          </el-form-item>
          <el-form-item label="体重">
            <el-input v-model="sizeForm.memberWeight"></el-input>
          </el-form-item>
        </el-form>
        <span slot="footer" class="dialog-footer">
          <el-button @click="dialogVisible = false">取 消</el-button>
          <el-button type="primary" @click="updateMember">确 定</el-button>
        </span>
      </el-dialog>
    </el-card>

  </div>

</template>

<script>
import moment from 'moment';
import {
  deleteMember,
  getAllMember,
  getByKeywordMember,
  memberExport,
  totalMember,
  totalMemberFuzzy,
  updateMember
} from "@/api/allApi";
import axios from "axios";

export default {
  name:"MemberManage",
  data() {
    return {
      dialogFormVisible: false,
      memberImportDialog:false,
      tableData: [],
      sizeForm:{

      },
      dialogVisible: false,
      /*分页*/
      pageSize:10,
      currentPage:1,
      total:0,
      searchValue:'',
      KeywordMember:'',
      // 允许的文件类型
      fileType: ["xls", "xlsx"],
      // 附件数量限制
      fileLimit: 1,
      //请求头
      headers: { "Content-Type": "multipart/form-data" },
      updateMessage:'',
      memberFile: {},
      fileList: [],
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
    getAllMember({
      page:0,
      size:10
    }).then(res=>{
      this.tableData = res.data
    }).catch(err=>{
      console.log(err.message)
    })

    totalMember().then(res=>{
      this.total = res.data.dataTotal
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
    editMember(value) {
      this.sizeForm = value
      this.sizeForm.memberPassword = ''
      this.dialogVisible = true
    },
    memberExport() {
      memberExport().then(res=>{
        window.open("/api/memberExport")
      }).catch(err=>{
        console.log(err.message)
      })
    },
    //上传文件之前
    beforeUpload(file){
      if (file.type != "" || file.type != null || file.type != undefined){
        //截取文件的后缀，判断文件类型
        const FileExt = file.name.replace(/.+\./, "").toLowerCase();
        //如果文件类型不在允许上传的范围内
        if(this.fileType.includes(FileExt)){
          return true;
        }
        else {
          this.$message.error("上传文件格式不正确!");
          return false;
        }
      }
    },
  //超出文件个数的回调
    handleExceed(){
      this.$message({
        type:'warning',
        message:'超出最大上传文件数量的限制！'
      });return
    },
  //上传文件的事件
    uploadFile(item){
      //上传文件的需要formdata类型;所以要转
      let FormDatas = new FormData()
      FormDatas.append('file',item.file);
      this.memberFile = FormDatas
    },
    memberImport() {
      axios({
        method: 'post',
        url: '/api/memberImport',
        headers: this.headers,
        timeout: 30000,
        data: this.memberFile
      }).then(res => {
        alert(res.data.message)
      }),
          this.$refs.upload.clearFiles()

          totalMember().then(res=>{
            this.total = res.data.dataTotal
          })
          getAllMember({
            page: 0,
            size: 10
          }).then(res => {
            this.tableData = res.data
          })
      this.memberImportDialog = false

    },

    deleteMember(memberNo){
      this.$confirm('此操作将永久删除该文件, 是否继续?', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        //确定 发送ajax请求
        deleteMember({memberNo:memberNo}).then(res=>{
          if(res.data.code===200){
            totalMember().then(res=>{
              this.total = res.data.dataTotal
            })
            //刷新表格
            getAllMember({
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
    updateMember() {

      updateMember({
        memberUsername: this.sizeForm.memberUsername,
        memberName: this.sizeForm.memberName,
        memberGender: this.sizeForm.memberGender,
        memberAge: this.sizeForm.memberAge,
        memberPhone: this.sizeForm.memberPhone,
        memberHeight: this.sizeForm.memberHeight,
        memberWeight: this.sizeForm.memberWeight,
        memberIntegral:this.sizeForm.memberIntegral,
        memberChange:this.sizeForm.memberChange,
        memberPower:this.sizeForm.memberPower,
        memberNo: this.sizeForm.memberNo,
      }).then(res=>{
        if(res.data.code===200){
          //刷新表格
          getAllMember({
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
     this.dialogVisible = false
    },
    changePage(params) {
      this.currentPage = (params-1) * this.pageSize
      this.getAllMember()
      this.currentPage = params
    },
    getByKeyword(KeywordMember) {
      totalMemberFuzzy({
        keyWord:KeywordMember
      }).then(res=>{
        this.total = res.data.dataTotal
      }),
      getByKeywordMember({
        keyWord:KeywordMember,
        page:0,
        size:10
      }).then(res=>{
        this.tableData = res.data
      }).catch(err=>{
        console.log(err.message)
      })

    },
    //获取数据方法
    getAllMember(){
      getAllMember({
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
    width: 120px;
  }

</style>