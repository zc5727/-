<template>
  <div class="login-container">
    <!-- 表单 表单验证:rules="rules" -->
    <el-form :model="ruleForm" :rules="rules"    
             status-icon
             ref="ruleForm"
             label-position="left"
             label-width="0px"
             class="demo-ruleForm login-page">
      <h1 class="title">系统登录</h1>
      <el-form-item prop="username">
        <el-input type="text"
                  v-model="ruleForm.adminAccount"
                  auto-complete="off"
                  placeholder="用户名"
                  class="input"
        ></el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input type="password"
                  v-model="ruleForm.adminPassword"
                  auto-complete="off"
                  placeholder="密码"
                  @keyup.enter.native="submitForm"
                  class="input"
        ></el-input>
      </el-form-item>

     
      <div style="margin: auto;">
        <el-radio-group v-model="identity" size="mini">
          <el-radio label="1" >会员登录</el-radio>
          <el-radio label="2" >管理员登录</el-radio>
        </el-radio-group>
      </div>
      <el-form-item style="width:100%;">
        <el-button type="primary" style="width:100%;" @click="submitForm" class="button" >登录</el-button>
      </el-form-item>
      <!--   没有账号，去注册   -->
      <el-link style="margin-bottom: 20px" type="success" @click="toRegister">没有账号?去注册</el-link>
    </el-form>
  </div>
</template>

<script>
import axios from "axios";
import {deleteEmployee, getAllEmployee, getMemberPassword} from "@/api/allApi";

export default {
  

    data(){
      return{
        identity:'1',
        ruleForm: {
          adminAccount: '',
          adminPassword: ''
        },
        rules: {
          adminAccount: [{required: true, message: '请输入用户名', trigger: 'blur'}],
          adminPassword: [{required: true, message: '请输入密码', trigger: 'blur'}]
        }
      }
    },
  methods: {

    
    submitForm() {
      if (this.identity === "2") {
        this.$refs.ruleForm.validate((valid) => {
          if (valid) {
            let _this = this
            axios.get('/api/getAdminPassword',{params:_this.ruleForm}).then(function (response) {
              if (response.data !=null) {
                window.localStorage.setItem('access-admin',JSON.stringify(response.data))
                _this.$router.replace({path:'/layout'})
              }
            })
          } else {
            console.log('error submit!!');
            return false;
          }
        });
      }
      else {
        this.$refs.ruleForm.validate((valid) => {
          if (valid) {
            let _this = this
            console.log(_this)

            let memberPhone = _this.ruleForm.adminAccount
            let memberPassword = _this.ruleForm.adminPassword
            getMemberPassword({
              memberPhone:memberPhone,
              memberPassword:memberPassword
            }).then(res=>{
              if (res.data.code === 200) {
                window.localStorage.setItem('access-member',JSON.stringify(res.data))
                _this.$router.replace({path:'/memberLayout'})
              }else {
                this.$message({
                  message: '账号或密码错误',
                  type: 'error'
                });
              }
            }).catch(err=>{
              console.log(err.message)
            })

          } else {
            console.log('error submit!!');
            return false;
          }
        });
      }

    },
    resetForm(formName) {
      this.$refs.ruleForm.resetFields();
    },
    toRegister() {
      this.$router.push({path: '/register'})
    }
  }
}

</script>

<style scoped>
.login-container {
  /* width: 100%;
  height: 100%; 
  text-align: center; */
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  position: relative;
  background-image: url(../assets/images/login.jpg);
  background-repeat: no-repeat;
  background-attachment: fixed;
  background-position: center;
  background-size: cover;
}
.login-page {
  /* -webkit-border-radius: 5px;
  border-radius: 5px;
  margin: 180px auto;
  width: 350px;
  padding: 35px 35px 15px;
  background: #fff;
  border: 1px solid #eaeaea;
  box-shadow: 0 0 25px #cac6c6; */
  position:absolute;
  top:50%;
  left:50%;
  transform: translate(-50%,-50%);
  width:50vw;
  min-width: 300px;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  background-color: rgba(0,0,0,0,0.7);
  border-radius: 15px;
  box-shadow: 0 15px 25px rgba(0,0,0,0.5);
}

.title {
  /* margin-bottom: 20px;
  color: #309975; */
  width:60%;
  margin:50px auto 0;
  color: rgb(7, 69, 63);
  text-align: center;

}

.input{
  text-align: center;
  padding: 10px;
}

.button{
  background-color: rgba(9,108,144,0.5);
  padding: 10px;
}


</style>
