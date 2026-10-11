<template>
  <div class="onlinePay">
    <div class="charge-header">
      <div class="charge-header-left">
        <b class="number">{{MemberIntegral}}</b> 积分
        <p>可用积分</p>
      </div>
      <div class="charge-header-middle">
        <b class="number">{{ MemberChange }}</b> 元
        <p>可用余额</p>
      </div>
      <div class="charge-header-right">
        <b class="number">{{TotalMoney}}</b> 元
        <p>充值总额</p>
      </div>
    </div>
    <div class="charge-recharge beautify-recharge">
      <div class="amount-select-title">选择充值金额</div>
      <div class="amount-grid">
        <div
          v-for="button in buttons"
          :key="button"
          :class="['amount-card', price === button ? 'amount-card-active' : '']"
          @click="yuan(button)"
        >
          ￥{{button}}.00
        </div>
      </div>
      <div class="pay-methods">
        <div
          class="pay-method"
          :class="payType === 'wx' ? 'pay-method-active' : ''"
          @click="selectPay('wx')"
        >
          <img src="../../assets/images/微信支付.png" class="pay-icon" />
          <span>微信支付</span>
        </div>
        <div
          class="pay-method"
          :class="payType === 'ali' ? 'pay-method-active' : ''"
          @click="selectPay('ali')"
        >
          <img src="../../assets/images/支付-支付宝.png" class="pay-icon" />
          <span>支付宝支付</span>
        </div>
        <div
          class="pay-method"
          :class="payType === 'union' ? 'pay-method-active' : ''"
          @click="selectPay('union')"
        >
          <img src="../../assets/images/银联.png" class="pay-icon" />
          <span>银联支付</span>
        </div>
      </div>
      <div class="pay-qrcode-area" v-if="payType && price">
        <div class="pay-qrcode-box">
          <img v-if="currentQr" :src="currentQr" class="pay-qrcode-img" />
          <div class="pay-qrcode-info">
            <div>{{ payType==='wx' ? '微信' : payType==='ali' ? '支付宝' : '银联' }}扫码支付</div>
            <div>支付 <span class="pay-qrcode-amount">￥{{price}}</span> 元</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import {
  addEmployee,
  addRechargeByMemberNo,
  getMemberChange,
  getMemberIntegral,
  getRechargeByMemberNo,
  getTotalMoney, updateMemberChange
} from "@/api/allApi";

export default {
  name: "OnlinePay",
  data() {
    return{
      buttons:['10','30','50','100','200'],
      admin:{},
      MemberIntegral:'',
      MemberChange:'',
      TotalMoney:'',
      price:'10',
      payType: '',
      currentQr: ''
    }
  },
  created() {
    this.admin = JSON.parse(window.localStorage.getItem('access-member'))
  },
  mounted() {
    this.getMemberIntegral()
    this.getTotalMoney()
    this.getMemberChange()
  },
  watch: {
    price(newVal) {
      this.updateQr();
    },
    payType(newVal) {
      this.updateQr();
    }
  },
  methods:{
    yuan(button){
      this.price = button
    },
    selectPay(type) {
      this.payType = type;
    },
    updateQr() {
      if (!this.payType) {
        this.currentQr = '';
        return;
      }
      if (this.payType === 'wx') {
        if (this.buttons.includes(this.price)) {
          this.currentQr = require(`@/assets/images/微信${this.price}.png`);
        } else {
          this.currentQr = require('@/assets/images/微信.png');
        }
      } else if (this.payType === 'ali') {
        if (this.buttons.includes(this.price)) {
          this.currentQr = require(`@/assets/images/支付宝${this.price}.png`);
        } else {
          this.currentQr = require('@/assets/images/支付宝.png');
        }
      } else if (this.payType === 'union') {
        this.currentQr = '';
        this.$message.info('暂未提供银联二维码');
      }
    },
    //立即支付
    pay(){
      let _this = this
      if(_this.price > 0){
        addRechargeByMemberNo({
          memberNo: _this.admin.memberNo,
          rechargeMoney:this.price,
        }).then(res=>{
          if(res.data.code===200){
            this.$message.success(res.data.message)
            updateMemberChange({
              memberNo:_this.admin.memberNo
            })
            this.getMemberChange()
            this.getTotalMoney()
          }else{
            this.$message.error(res.data.message)
          }
        }).catch(err=>{
          console.log(err)
        })
      } else{
        this.$message.error('请选择充值金额')
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
    },

    getTotalMoney(){
      let _this = this
      getTotalMoney({
        memberNo: _this.admin.memberNo}).then(res=>{
        this.TotalMoney = res.data
      }).catch(err=>{
        console.log(err.message)
      })
    },
    getMemberChange(){
      let _this = this
      getMemberChange({
        memberNo: _this.admin.memberNo}).then(res=>{
        this.MemberChange = res.data
      }).catch(err=>{
        console.log(err.message)
      })
    },
  }
}
</script>

<style scoped>
.onlinePay {
  padding: 40px;
}

.charge-header {
  background-color: #c2a7a7;

}

.charge-header-left ,
.charge-header-middle,
.charge-header-right
{
  float: left;
  width: 293px;
  height: 125px;
  margin-right: 20px;
  border: 1px solid #00c996;
  border-radius: 5px;
  padding: 20px;
}

.charge-header-left p,
.charge-header-middle p,
.charge-header-right p{
  margin-top: 15px;
  font-size: 14px;
  color: #8a92a9;

}

.charge-header-right {
  margin-right: 0px;
}

.charge-recharge {
  float: left;
  width: 600px;
  height: 43px;
  margin: 60px 160px;
  text-align: center;

}

.charge-recharge-middle {
  margin: auto;
}

.charge-recharge-middle-button {
  width: 600px;
  padding-top: 60px;
}


.number {
  font-size: 32px;
}

.payIcon {
  text-align: center;
  width:600px;
  height: 45px;
  margin-top: 30px;
}

.payIcon a{
  width: 45px;
  height: 45px;
  margin-right: 10px;
}

.payIcon a img{
  width: 45px;
  height: 45px;
}

.selected-pay {
  border: 2px solid #409EFF;
  border-radius: 8px;
  box-sizing: border-box;
}

.beautify-recharge {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  padding: 16px 32px 24px 32px;
  width: 480px;
  margin: 40px auto 0 auto;
}
.amount-select-title {
  font-size: 18px;
  font-weight: bold;
  margin-top: -8px;
  margin-bottom: 10px;
  color: #333;
  display: flex;
  justify-content: center;
  align-items: center;
}
.amount-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 12px;
  margin-bottom: 28px;
}
.amount-card {
  width: 108px;
  height: 48px;
  background: #fafbfc;
  border: 1.5px solid #e4e7ed;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #333;
  cursor: pointer;
  transition: all 0.2s;
}
.amount-card-active {
  border: 2px solid #409EFF;
  background: #f0f7ff;
  color: #409EFF;
}
.pay-methods {
  display: flex;
  gap: 24px;
  margin-bottom: 32px;
  margin-top: 10px;
}
.pay-method {
  display: flex;
  align-items: center;
  border: 1.5px solid #e4e7ed;
  border-radius: 8px;
  padding: 8px 18px;
  cursor: pointer;
  font-size: 16px;
  color: #333;
  background: #fafbfc;
  transition: all 0.2s;
}
.pay-method-active {
  border: 2px solid #409EFF;
  background: #f0f7ff;
  color: #409EFF;
}
.pay-icon {
  width: 28px;
  height: 28px;
  margin-right: 8px;
}
.pay-qrcode-area {
  display: flex;
  justify-content: center;
  margin-top: -4px;
}
.pay-qrcode-box {
  background: #fafbfc;
  border-radius: 10px;
  padding: 18px 28px 12px 28px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.pay-qrcode-img {
  width: 180px;
  height: 240px;
  object-fit: contain;
  margin-bottom: 12px;
}
.pay-qrcode-info {
  text-align: center;
  color: #333;
  font-size: 15px;
}
.pay-qrcode-amount {
  color: #f56c6c;
  font-weight: bold;
  font-size: 18px;
}
</style>