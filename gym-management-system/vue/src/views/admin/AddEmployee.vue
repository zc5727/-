<template>
  <div class="addEmployee">
    <el-card shadow="hover">
      <el-page-header @back="goBack" content="新增员工" style="margin: 20px">
      </el-page-header>
      <div class="box-card bgc">
        <div class="text item">
          <el-form ref="sizeForm" :model="sizeForm" label-width="80px" size="mini" >
            <el-form-item label="姓名">
              <el-input v-model="sizeForm.employeeName"></el-input>
            </el-form-item>
            <el-form-item label="性别">
              <el-select v-model="sizeForm.employeeGender" placeholder="请选择性别">
                <el-option label="男" value="M"></el-option>
                <el-option label="女" value="F"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="年龄">
              <el-input v-model="sizeForm.employeeAge"></el-input>
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
            <el-form-item label="备注">
              <el-input v-model="sizeForm.employeeMessage"></el-input>
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
            <el-form-item size="small">
              <el-button type="primary" @click="onSubmit">确认新增</el-button>
              <el-button>取消</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </el-card>

  </div>
</template>

<script>
import {addEmployee} from "@/api/allApi";

export default {
  data() {
    return {
      sizeForm: {
        memberName: '',
        memberGender: '',
        memberAge: '',
        memberPhone: '',
        memberHeight: '',
        memberWeight: '',
        cardClass: '',
      }
    };
  },
  methods: {
    onSubmit() {
      addEmployee({
        employeeName: this.sizeForm.employeeName,
        employeeGender: this.sizeForm.employeeGender,
        employeeAge: this.sizeForm.employeeAge,
        employeePhone: this.sizeForm.employeePhone,
        employeeJob: this.sizeForm.employeeJob,
        employeeMessage:this.sizeForm.employeeMessage,
      }).then(res=>{
        if(res.data.code===200){
          alert(res.data.message)
        }else{
          alert(res.data.message)
        }
      }).catch(err=>{
        console.log(err)
      })
    },
    goBack() {
      this.$router.back();
    }
  }
};
</script>

<style scoped>
@import "../../assets/css/home.css";
</style>


<style scoped>


.text {
  font-size: 14px;
}

.item {
  padding: 20px 40px;
  width: 70%;
}

.box-card {
  width: 100%;
  border-radius: 10px;
}

.addEmployee {
  width: 100%;
  margin: 20px;

}

.bgc {
  background:rgba(0,0,0,.02)
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
