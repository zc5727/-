<template>
  <div class="addMember">
    <el-card  shadow="hover">
      <el-page-header @back="goBack" content="新增用户" style="margin: 20px">
      </el-page-header>
      <div class="box-card bgc">
      <div class="text item">
        <el-form ref="sizeForm" :model="sizeForm" label-width="80px" size="mini" :rules="rules">
          <el-form-item label="姓名" prop="memberName">
            <el-input v-model="sizeForm.memberName" clearable></el-input>
          </el-form-item>
          <el-form-item label="性别" prop="memberGender">
            <el-select v-model="sizeForm.memberGender" placeholder="请选择性别" >
              <el-option label="男" value="M"></el-option>
              <el-option label="女" value="F"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="年龄" prop="memberAge">
            <el-input v-model="sizeForm.memberAge" clearable></el-input>
          </el-form-item>
          <el-form-item label="手机号" prop="memberPhone">
            <el-input v-model="sizeForm.memberPhone" clearable></el-input>
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
              <el-option label="包年vip" value="3"></el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="身高" prop="memberHeight">
            <el-input v-model="sizeForm.memberHeight" clearable></el-input>
          </el-form-item>
          <el-form-item label="体重" prop="memberWeight">
            <el-input v-model="sizeForm.memberWeight" clearable></el-input>
          </el-form-item>
          <el-form-item label="购买时长">
            <el-input v-model="sizeForm.cardClass" clearable></el-input>
          </el-form-item>
          <el-form-item size="large">
            <el-button type="primary" @click="onSubmit">确认新增</el-button>
            <el-button @click="resetForm('sizeForm')">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
      </div>
    </el-card>

  </div>
</template>

<script>
import {addMember} from "@/api/allApi";
import valid from "@/utils/valid";


export default {
  data() {
    let isInteger =(rule, value, callback) => {
      if (!value) {
        return callback(new Error('输入不可以为空'));
      }
      setTimeout(() => {
        if (!Number(value)) {
          callback(new Error('请输入正整数'));
        } else {
          const re = /^[0-9]*[1-9][0-9]*$/;
          const rsCheck = re.test(value);
          if (!rsCheck) {
            callback(new Error('请输入正整数'));
          } else {
            callback();
          }
        }
      }, 0);
    }

    return {
      sizeForm: {
        memberName: '',
        memberGender: '',
        memberAge: '',
        memberPhone: '',
        memberHeight: '',
        memberWeight: '',
        cardClass: '',
        memberIntegral:'',
        memberChange:'',
        memberPower:'',
      },
      rules: {
        memberName: [{required:true,message:'请输入姓名',trigger:'blur'}],
        memberGender: [{required:true,message:'请选择性别',trigger:'blur'}],
        memberAge: [
          {required:true,message:'请输入年龄',trigger:'blur'},
          {
            required: true,
            pattern: /^((1[0-5])|[1-9])?\d$/,
            message: '请输入合法的年龄！',
            trigger: 'change'
          }
        ],
        memberPhone: [
          { required: true, message: '请输入手机号码', trigger: 'blur' },
          {
            required: true,
            pattern: /^(0|86|17951)?(13[0-9]|15[012356789]|17[678]|18[0-9]|14[57])[0-9]{8}$/,
            message: '请输入合法的手机号！',
            trigger: ['change','blur']
          }
        ],
        memberWeight: [
          {
            pattern:   /^(0(\.\d{1}){0,1}|[1-8]\d{1,3}(\.\d{1}){0,1}|9\d{1,2}(\.\d{1}){0,1}|999(\.0){0,1}|.{0})$/,
            message: '请输入合法的体重!',
            trigger: 'change'
          }
        ],
        memberPower:[{required:true,message:'请选择会员等级',trigger:'blur'}],
        memberHeight: [
          {
            pattern:    /^(0{1}|[1-9]\d{0,3}|.{0})$/,
            message: '请输入合法的身高!',
            trigger: 'change'
          }
        ],
        cardClass: [{required:true,message:'请输入姓名',trigger:'blur'}],
        memberIntegral:[{required:true,message:'请输入积分',trigger:'blur'}, { validator: isInteger, trigger: 'blur' }],
        memberChange:[ {required:true,message:'请输入余额',trigger:'blur'},{ validator: isInteger, trigger: 'blur' }],
      },
    };
  },
  methods: {
    onSubmit() {
      addMember({
        memberName: this.sizeForm.memberName,
        memberGender: this.sizeForm.memberGender,
        memberAge: this.sizeForm.memberAge,
        memberPhone: this.sizeForm.memberPhone,
        memberHeight: this.sizeForm.memberHeight,
        memberWeight: this.sizeForm.memberWeight,
        cardClass:this.sizeForm.cardClass,
        memberIntegral:this.sizeForm.memberIntegral,
        memberChange:this.sizeForm.memberChange,
        memberPower:this.sizeForm.memberPower
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
    },
    resetForm(sizeForm) {
      this.$refs[sizeForm].resetFields();
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

  .addMember {
    width: 100%;
    margin: 20px;
  }
  
  .bgc {
    background:rgba(0,0,0,.02)
  }
</style>
