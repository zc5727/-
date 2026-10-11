<template>
  <div class="layout">
      <!--  头部-->
      <Header />
      <!--  主体  -->
      <div style="display: flex" >
        <!--  侧边栏  -->
        <Aside />
        <!--  内容区域  -->
        <div class="content">
          <router-view />
        </div>
      </div>
  </div>
</template>

<script>
import Header from "@/components/admin/Header";
import Aside from "@/components/admin/Aside";


export default {
  name: "Layout",
  data() {
    return {
      flag:'',
    }
  },
  components:{
    Header,
    Aside,
  }
}
</script>

<style scoped>
  .layout {
    position: relative;
    display: flex;
  }
  .content {
    position: absolute;
    top: 70px;
    left: 200px;
    width: 84%;

  }
</style>