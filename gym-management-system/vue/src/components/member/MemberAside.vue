<template>
  <div class="memberAside">
    <div class="memberAside-top">
      <a href="javascript:;" class="edit-avatar" title="点击修改头像">
        <img src="../../assets/images/avatar.png" alt="" class="touxiang"></a>
      <h2>{{memberPhone}}</h2>
      <div class="mobantu-check">
        <a href="javascript:;" class="usercheck checkin" :class="{changeCheck:isActive }" data-wpel-link="internal" @click="checkIn">{{ checkin }}</a>
        <p>每日签到送积分</p>
      </div>
    </div>
    <div class="memberAside-bottom">
      <ul v-for="option in options" >
        <li class="usermenu-charge" @click="toContent(option.url)">
          <a href="javascript:;">{{option.name}}</a>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>

import {addCheckIn, getMemberIntegral, updateMemberIntegral} from "@/api/allApi";

export default {
  name: "MemberAside",
  data(){
    return{
      options:[
          {name:'全部课程',url:'allCourse'},
          {name:'在线充值',url:'onlinePay'},
          {name:'充值记录',url:'rechargeRecord'},
          {name:'升级VIP',url:'vipCard'},
          {name:'购买记录',url:'buyRecord'},
          {name:'我的资料',url:'myProfile'},
          {name:'修改密码',url:'changePassword'},
        ],
      isActive:false,
      checkin:'今日签到',
      checkTime:'',
      today:'',
      memberPhone:'',
      member:{},
    }
  },
  created() {
    this.member = JSON.parse(window.localStorage.getItem('access-member'))
    this.checkTime = window.localStorage.getItem('access-checkTime')
  },
  mounted() {
    let date = new Date()
    let year = date.getFullYear();
    let month = date.getMonth() + 1;
    let day = date.getDay();
    month = (month > 9) ? month : ("0" + month);
    day = (day < 10) ? ("0" + day) : day;
    let today = year + "-" + month + "-" + day;
    this.today = today
    if (today == this.checkTime) {
      this.checkin = '今日已签'
      this.isActive=true
    }
    else {
      this.checkin = '今日签到'
      this.isActive=false
    }
    this.memberPhone = this.admin.memberPhone
  },
  methods:{
    toContent(url) {
      this.$router.push({path:url})
    },
    checkIn() {
      if (this.today != this.checkTime) {
        window.localStorage.setItem('access-checkTime', this.today)
        this.isActive = true
        this.checkin = '今日已签'
        addCheckIn({
          memberNo: this.admin.memberNo
        }).then(res => {
          alert(res.data.message)
          updateMemberIntegral({
            memberNo:this.admin.memberNo,
            price:-1
          })
          this.getMemberIntegral()
        }).catch(err => {
          alert(err.data.message)
        })
      }else {
      }
    },
    getMemberIntegral(){
      let _this = this
      getMemberIntegral({
        memberNo: _this.admin.memberNo}).then(res=>{
        this.MemberIntegral = res.data
      }).catch(err=>{
        console.log(err.message)
      })
    }


  }
}
</script>

<style scoped>

li {
  list-style: none;
}

a {
  text-decoration: none;
}
.memberAside {
  float: left;
  width: 200px;
  height: 100%;
  background-color: #fbfbfb;
  padding: 20px;
}

.memberAside-top {
  width: 160px;
  height: 160px;
  margin-bottom: 15px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eaeaea;
  overflow: hidden;
  text-align: center;
}

.memberAside-top .edit-avatar {
  position: relative;
  display: inline-block;
  margin-bottom: 10px;
  width: 50px;
  height: 50px;
}

.memberAside-top .touxiang {
  border-radius:50%;
  border:1px solid #00c996;
  width: 50px;
  height: 100%;
}



.memberAside-top img {
  max-width:100%;
  max-height: 100%;
  vertical-align:middle;
}

.mobantu-check {
  padding-top: 15px;
}

.usercheck {
  background: #ff5f33;
  color: #fff !important;
  border-radius: 15px;
  padding: 3px 12px;
  font-size: 12px;
  display: inline-block;
  width: 76px;
}

.changeCheck {
  background: #aaa;
  color:#fff ;
}

.mobantu-check p {
  font-size: 12px;
  color: #8a92a9;
  margin-top: 3px;
}

.memberAside-top h2 {
  display: block;
  font-size: 14px;
  font-weight: 400;
}
.memberAside-bottom {

}
.memberAside-bottom ul {

}

.memberAside-bottom ul li {

}

.memberAside-bottom ul li a{
  display: block;
  font-size: 16px;
  text-decoration: none;
  padding: 5px 20px;
  border-radius:2px;
  color: #888;
}
.memberAside-bottom .usermenu-charge {
  width: 160px;
  height: 50px;
  margin-bottom: 5px;
  line-height: 50px;
  text-align: center;
}

active {
  color: #666;
  background-color: #eee;
}

</style>
