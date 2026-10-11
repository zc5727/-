<template>
  <div class="header">
    <div style="width: 200px; padding-left: 30px;font-weight: bold;color:#309975;" >
      健身房后台管理
    </div>
    <div style="flex: 1"></div>
    <div style="width: 100px">

      <el-row class="block-col-2">
          <el-dropdown>
            <div class="adminer">
              <span class="el-dropdown-link">{{admin.adminAccount}}<i class="el-icon-arrow-down el-icon--right"></i></span>
            </div>

            <el-dropdown-menu slot="dropdown" >
              <el-dropdown-item @click.native="logout">退出系统</el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>

      </el-row>
    </div>
  </div>
</template>


<script>


export default {
  name: "Header",
  data() {
    return {
      admin: {

      },
    }
  },
  created() {
    this.admin = JSON.parse(window.localStorage.getItem('access-admin'))

  },
  methods:{
    logout() {

      this.$router.push({name:'Login'})
      //退出登陆，清空token
      localStorage.removeItem('access-admin');
      this.$router.push('/login');
    },
  }
}


</script>

<style scoped>

.header {
  height:70px;
  width: 100%;
  line-height: 70px;
  border-bottom: 1px solid #ccc;
  display: flex;
  position: fixed;
  top:0;
  left: 0;
  background-color: #fff;
  z-index: 300;
}
.adminer {
  display: flex;
  height:70px;
  line-height: 70px;
}



</style>