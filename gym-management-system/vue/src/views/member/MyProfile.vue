<template>
  <div class="myProfile">
    <div class="myProfile-form">
      <el-form ref="form" :model="memberForm" label-width="80px" size="mini">
        <el-form-item label="用户名">
          <el-input v-model="memberForm.memberUsername" disabled></el-input>
        </el-form-item>
        <el-form-item label="注册时间">
          <el-input v-model="memberForm.cardTime" disabled></el-input>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="memberForm.memberName"></el-input>
        </el-form-item>
        <el-form-item label="年龄">
          <el-input v-model="memberForm.memberAge"></el-input>
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="memberForm.memberPhone"></el-input>
        </el-form-item>
        <el-form-item label="身高">
          <el-input v-model="memberForm.memberHeight"></el-input>
        </el-form-item>
        <el-form-item label="体重">
          <el-input v-model="memberForm.memberWeight"></el-input>
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="memberForm.memberGender" placeholder="性别">
            <el-option label="男" value="M"></el-option>
            <el-option label="女" value="F"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="个性签名">
          <el-input type="textarea" v-model="memberForm.personalizedSignature"></el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSubmit">修改资料</el-button>
        </el-form-item>
      </el-form>
    </div>

  </div>

</template>

<script>
import { getMemberByMemberNo, updateMemberByMemberNo} from "@/api/allApi";
import moment from "moment";

export default {
  data() {
    return {
      memberForm: {

      },
      member: {

      }
    }
  },
  created() {
    this.member = JSON.parse(window.localStorage.getItem('access-member'))
  },
  mounted() {
    this.getMemberByMemberNo()
  },
  filters:{
    dataFormat(value){
      return moment(value).format("YYYY-MM-DD")
    }
  },
  methods: {
    onSubmit() {
      updateMemberByMemberNo({
        memberName: this.memberForm.memberName,
        memberGender: this.memberForm.memberGender,
        memberAge: this.memberForm.memberAge,
        memberPhone: this.memberForm.memberPhone,
        memberHeight: this.memberForm.memberHeight,
        memberWeight: this.memberForm.memberWeight,
        personalizedSignature:this.memberForm.personalizedSignature,
        memberNo: this.member.memberNo
      }).then(res => {
        if (res.data.code === 200) {
          alert(res.data.message)
        } else {
          alert(res.data.message)
        }
      }).catch(err => {
        console.log(err.message)
      })
    },
    getMemberByMemberNo() {
      getMemberByMemberNo({
        memberNo: this.member.memberNo
      }).then(res=>{
        this.memberForm = res.data
      }).catch(err=>{
        console.log(err.message)
      })
    }
  }
}
</script>

<style scoped>
.myProfile {
  padding: 40px;
}

.myProfile-form {
  padding: 20px;
  width: 500px;
}
</style>
