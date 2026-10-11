import Vue from 'vue'
import VueRouter from 'vue-router'
import MemberManage from "@/views/admin/MemberManage";
import Index from "@/views/admin/Index.vue";
import AddMembers from "@/views/admin/AddMembers";
import CourseManage from "@/views/admin/CourseManage";
import EquipmentManage from "@/views/admin/EquipmentManage";
import EmployeeManage from "@/views/admin/EmployeeManage";
import AddEmployee from "@/views/admin/AddEmployee";
import Layout from "@/layout/Layout";
import Login from "@/views/Login";
import RegisterManage from "@/views/admin/RegisterManage";
import MemberLayout from "@/layout/MemberLayout";
import VipCard from "@/views/member/VipCard";
import RechargeRecord from "@/views/member/RechargeRecord";
import OnlinePay from "@/views/member/OnlinePay";
import MyProfile from "@/views/member/MyProfile";
import ChangePassword from "@/views/member/ChangePassword";
import BuyRecord from "@/views/member/BuyRecord";
import axios from "axios";
import Error from "@/components/Error";
import AllCourse from "@/views/member/allCourse";
import Register from "@/views/Register.vue";

Vue.use(VueRouter)
// 解决ElementUI导航栏中的vue-router在3.0版本以上重复点菜单报错问题
const originalPush = VueRouter.prototype.push
VueRouter.prototype.push = function push(location) {
  return originalPush.call(this, location).catch(err => err)
}


const routes = [
  {
    path:'/layout',
    name:'Layout',
    component:Layout,
    redirect:Index,
    children:[
      {
        path:'index',
        name:'Index',
        component:Index,
      },
      {
        path:'memberManage',
        name:'MemberManage',
        component:MemberManage
      },
      {
        path:'addMembers',
        name:'AddMembers',
        component:AddMembers
      },
      {
        path:'courseManage',
        name:'CourseManage',
        component:CourseManage
      },
      {
        path:'equipmentManage',
        name:'EquipmentManage',
        component:EquipmentManage
      },
      {
        path:'employeeManage',
        name:'EmployeeManage',
        component:EmployeeManage
      },
      {
        path:'addEmployee',
        name:'AddEmployee',
        component:AddEmployee
      },
      {
        path:'registerManage',
        name:'RegisterManage',
        component:RegisterManage
      }
    ]
  },
  {
    path:'/',
    name:'First',
    component:Login,
    children: [
      {
        path:'/login',
        name:'Login',
        component:Login,
      }
    ]
  },
  {
    path:'/register',
    name:'Register',
    component:Register,
  },
  {
    path:'/error',
    name:'Error',
    component:Error,
  },
  {
    path: '/memberLayout',
    name: 'MemberLayout',
    component: MemberLayout,
    redirect:'/memberLayout/allCourse',
    children:[
      {
        path:'buyRecord',
        name:'BuyRecord',
        component:BuyRecord,
      },
      {
        path:'allCourse',
        name:'AllCourse',
        component:AllCourse,
      },
      {
        path:'changePassword',
        name:'ChangePassword',
        component:ChangePassword
      },
      {
        path:'myProfile',
        name:'MyProfile',
        component:MyProfile
      },
      {
        path:'onlinePay',
        name:'OnlinePay',
        component:OnlinePay
      },
      {
        path:'rechargeRecord',
        name:'RechargeRecord',
        component:RechargeRecord
      },
      {
        path:'vipCard',
        name:'VipCard',
        component:VipCard
      },
    ]
  }

]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})


router.beforeEach((to, from, next) => {
  if (to.path.startsWith('/login') || to.path === '/register') {
    window.localStorage.removeItem('access-admin')
    window.localStorage.removeItem('access-member')
    next()
  } else {
    let admin = JSON.parse(window.localStorage.getItem('access-admin'))
    let member = JSON.parse(window.localStorage.getItem('access-member'))
    if (!admin && !member) {
      next({path: '/login'})
    } else {
      if(admin){
        //校验token合法性
        axios({
          url:'/api/checkToken',
          method:'post',
          headers:{
            token:admin.token
          }
        }).then((response) => {
          console.log(response.data)
          if(!response.data){
            console.log('校验失败')
            next({path: '/error'})
          }
        })
        next()
      }else if (member){
        //校验token合法性
        axios({
          url:'/api/checkToken',
          method:'post',
          headers:{
            token:member.token
          }
        }).then((response) => {
          console.log(response.data)
          if(!response.data){
            console.log('校验失败')
            next({path: '/error'})
          }
        })
        next()
      }

    }
  }
})

export default router
