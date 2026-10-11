<template>
  <div class="aside">
    <div class="sideMenu">
      <el-menu
          style="width: 100%; min-height: calc(100vh - 50px);"
          default-active="2"
          class="el-menu-vertical-demo"
          @open="handleOpen"
          @close="handleClose">
        <el-menu-item index="0" @click="toIndex">
          <i class="el-icon-monitor"></i>
          <span slot="title">首页</span>
        </el-menu-item>
        <el-submenu index="1">
          <template slot="title">
            <i class="el-icon-user-solid"></i>
            <span>会员管理</span>
          </template>
          <el-menu-item index="1-1" @click="toMemberManage">会员管理</el-menu-item>
          <el-menu-item index="1-2" @click="toAddMembers">新增会员</el-menu-item>
        </el-submenu>
        <el-submenu index="2" >
          <template slot="title">
            <i class="el-icon-s-home"></i>
            <span>健身房管理</span>
          </template>
          <el-menu-item index="2-1" @click="toCourseManage">课程管理</el-menu-item>
          <el-menu-item index="2-2" @click="toEquipmentManage">器材管理</el-menu-item>
        </el-submenu>
        <el-submenu index="3">
          <template slot="title">
            <i class="el-icon-s-custom"></i>
            <span>员工管理</span>
          </template>
          <el-menu-item index="3-1" @click="toEmployeeManage">查看员工</el-menu-item>
          <el-menu-item index="3-2" @click="toAddEmployee">新增员工</el-menu-item>
        </el-submenu>
        <el-submenu index="4">
          <template slot="title">
            <i class="el-icon-s-order"></i>
            <span>报名表管理</span>
          </template>
          <el-menu-item index="4-1" @click="toRegisterManage">查看报名表</el-menu-item>
        </el-submenu>
      </el-menu>
    </div>

  </div>
</template>

<script>
export default {
  name: "Aside",
  data() {
    return {

    }
  },
  methods: {
    handleOpen(key, keyPath) {

    },
    handleClose(key, keyPath) {

    },
    toIndex() {
      this.$router.push('/layout/index')
    },
    toMemberManage() {
      this.$router.push('/layout/MemberManage')
    },
    toAddMembers() {
      this.$router.push('/layout/AddMembers')
    },
    toCourseManage() {
      this.$router.push('/layout/CourseManage')
    },
    toEquipmentManage() {
      this.$router.push('/layout/EquipmentManage')
    },
    toEmployeeManage() {
      this.$router.push('/layout/EmployeeManage')
    },
    toAddEmployee() {
      this.$router.push('/layout/AddEmployee')
    },
    toRegisterManage() {
      this.$router.push('/layout/RegisterManage')
    },
  }
}

</script>

<style scoped>
  .sideMenu {
    width: 200px;
    height: 100%;
  }

  .aside {
    position: fixed;
    top: 70px;
    left: 0;
  }
</style>

