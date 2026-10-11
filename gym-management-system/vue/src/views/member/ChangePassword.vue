<template>
  <div class="changePassword">
    <el-form :model="ruleForm" status-icon :rules="rules" ref="ruleForm" label-width="100px" class="demo-ruleForm">
      <el-form-item label="原密码" prop="oldPass">
        <el-input type="password" v-model="ruleForm.oldPass" autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item label="密码" prop="pass">
        <el-input type="password" v-model="ruleForm.pass" autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item label="确认密码" prop="checkPass">
        <el-input type="password" v-model="ruleForm.checkPass" autocomplete="off"></el-input>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submitForm('ruleForm')">提交</el-button>
        <el-button @click="resetForm('ruleForm')">重置</el-button>
      </el-form-item>
    </el-form>
  </div>

</template>

<script>
import axios from "axios";
import {getMemberPassword, updateMemberPassword} from "@/api/allApi";

export default {
  data() {
    var validatePass = (rule, value, callback) => {
      if (value === '') {
        callback(new Error('请输入密码'));
      } else {
        if (this.ruleForm.checkPass !== '') {
          this.$refs.ruleForm.validateField('checkPass');
        }
        callback();
      }
    };
    var validatePass2 = (rule, value, callback) => {
      if (value === '') {
        callback(new Error('请再次输入密码'));
      } else if (value !== this.ruleForm.pass) {
        callback(new Error('两次输入密码不一致!'));
      } else {
        callback();
      }
    };
    return {
      ruleForm: {
        oldPass:'',
        pass: '',
        checkPass: '',
      },
      rules: {
        pass: [
          { validator: validatePass, trigger: 'blur' }
        ],
        checkPass: [
          { validator: validatePass2, trigger: 'blur' }
        ],
      },
      admin:{

      },
    };
  },
  created() {
    this.admin = JSON.parse(window.localStorage.getItem('access-member'))
  },
  methods: {
    submitForm(formName) {
      this.$refs.ruleForm.validate((valid) => {
        if (valid) {
          let _this = this
          console.log(_this)
          let memberPhone = _this.admin.memberPhone
          let oldPass = _this.ruleForm.oldPass
          let memberPassword =_this.ruleForm.pass
          //根据手机号 和 老密码同时搜索（就是登录接口） 若老密码输入错误，提示原密码错误
          getMemberPassword({
            memberPhone:memberPhone,
            memberPassword:oldPass
          }).then(res=>{
            if (res.data !=null) {
              //直接通过手机号，新密码修改
              updateMemberPassword({
                memberPhone:memberPhone,
                memberPassword:memberPassword
              }).then(res=>{
                alert(res.data.message)
                //修改完成之后token失效
                //退出登陆，清空token
                localStorage.removeItem('access-member');
                this.$router.push('/login');
              }).catch(err=>{
                console.log(err.message)
              })

            }
          }).catch(err=>{
            alert('原密码输入错误')
          })

        } else {
          console.log('error submit!!');
          return false;
        }
      });
    }
    },
    resetForm(formName) {
      this.$refs[formName].resetFields();
    }
}
</script>

<style scoped>
.changePassword {
  padding: 40px;
}

.demo-ruleForm {
  width: 500px;
}
</style>