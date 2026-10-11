<template>
  <div class="login-container">
    <!-- 表单 表单验证:rules="rules" -->
    <el-form :model="ruleForm" :rules="rules"    
             status-icon
             ref="ruleForm"
             label-position="left"
             label-width="0px"
             class="demo-ruleForm login-page">
      <h1 class="title">会员注册</h1>
      <el-form-item prop="memberPhone">
        <el-input type="text"
                  v-model="ruleForm.memberPhone"
                  auto-complete="off"
                  placeholder="用户名(手机号)"
                  class="input"
        ></el-input>
      </el-form-item>
      <el-form-item prop="memberPassword">
        <el-input type="password"
                  v-model="ruleForm.memberPassword"
                  auto-complete="off"
                  placeholder="密码"
                  class="input"
        ></el-input>
      </el-form-item>
      <el-form-item prop="memberPassword2">
        <el-input type="password"
                  v-model="ruleForm.memberPassword2"
                  auto-complete="off"
                  placeholder="确认密码"
                  class="input"
        ></el-input>
      </el-form-item>
      <el-form-item style="width:100%;">
        <el-button type="primary" style="width:100%;" @click="submitForm" class="button" >注册</el-button>
      </el-form-item>
      <!--   没有账号，去注册   -->
      <el-link style="margin-bottom: 20px" type="success" @click="toLogin">已有账号?去登录</el-link>
    </el-form>
  </div>
</template>

<script>
import {register} from "@/api/allApi";


export default {

    data(){
      return{
        identity:'1',
        ruleForm: {
          memberPhone: '',
          memberPassword: '',
          memberPassword2: ''
        },
        rules: {
          memberPhone: [{required: true, message: '请输入用户名', trigger: 'blur'}],
          memberPassword: [{required: true, message: '请输入密码', trigger: 'blur'}],
          memberPassword2: [{required: true, message: '请输入确认密码', trigger: 'blur'}]
        }
      }
    },
  methods: {

    
    submitForm() {
      // 校验表单
      this.$refs.ruleForm.validate((valid) => {
        if (valid) {
          // 表单校验通过
          //判断密码和确认密码是否相同
          if(this.ruleForm.memberPassword !== this.ruleForm.memberPassword2){
            this.$message({
              message: '两次密码不一致',
              type: 'error'
            })
            return false;
          }
          // 发送请求
          register(this.ruleForm).then(response => {
              if (response.data.code === 200) {
                this.$message({
                  message: '注册成功',
                  type: 'success'
                })
                this.$router.push({name: 'Login'})
              } else {
                this.$message({
                  message: '注册失败',
                  type: 'error'
                })
              }
            })
            .catch(error => {
              console.log(error)
            })
        } else {
          // 表单校验不通过
          return false;
        }
      })

    },
    toLogin() {
      this.$router.push({path: '/login'})
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
