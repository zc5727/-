<template>
  <div class="buyRecord">
    <el-table
        :data="tableData"
        stripe
        height="520px"
        style="width: 100%;height: 85%">
      <el-table-column
          type="index"
          :index="indexMethod"
          label="序号"
          style="height:50px">
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
          prop="memberPhone"
          label="会员手机号">
      </el-table-column>
      <el-table-column
          label="操作"
          width="100">
        <template slot-scope="scope">
          <el-button type="danger" size="mini" @click="handleDelete(scope.row)">退课</el-button>
        </template>
      </el-table-column>
    </el-table>



  </div>

</template>

<script>
import {getAllRegister, getRegisterByMemberNo, totalRegister, deleteRegister} from "@/api/allApi";
import moment from "moment";

export default {
  data() {
    return {
      tableData: [],
      admin:{

      },
    }
  },
  computed: {
    indexMethod(){

    }
  },
  filters:{
    dataFormat(value){
      return moment(value).format("YYYY-MM-DD")
    }
  },
  created() {
    this.admin = JSON.parse(window.localStorage.getItem('access-member'))
  },
  mounted() {
    this.getRegisterByMemberNo()
  },
  methods:{
    getRegisterByMemberNo(){
      let _this = this
      getRegisterByMemberNo({
        memberNo:_this.admin.memberNo,
      }).then(res=>{
        this.tableData = res.data
      }).catch(err=>{
        console.log(err.message)
      })
    },
    handleDelete(row) {
      this.$confirm('确定要退掉该课程吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        deleteRegister({ registerNo: row.registerNo }).then(res => {
          if(res.data.code === 200){
            this.$message.success('退课成功！');
            this.getRegisterByMemberNo();
          }else{
            this.$message.error(res.data.message);
          }
        }).catch(err => {
          this.$message.error('退课失败');
        });
      });
    }
  }
}
</script>
<style scoped>
.buyRecord {
  padding: 40px;
  width: 1000px;
  height: 650px;
}
</style>