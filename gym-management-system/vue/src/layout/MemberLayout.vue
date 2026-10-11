<template>
  <div class="member-layout">
   <div class="w">
     <!--  头部-->
     <MemberHeader />
     <!--  主体  -->
     <div class="member-body">
       <!--  侧边栏  -->
       <MemberAside />
       <!--  内容区域  -->
       <div class="content">
         <router-view />
       </div>
     </div>
   </div>
  </div>
</template>

<script>
import MemberHeader from "@/components/member/MemberHeader";
import MemberAside from "@/components/member/MemberAside";


export default {
  name: "MemberLayout",
  data(){
    return{
      flag:'',
    }
  },
  components:{
    MemberHeader,
    MemberAside,
  }
}
</script>

<style scoped>
body{
  background-color: #000;
}
.member-layout {

}

.w {
  width: 1200px;
  margin: auto;

}

.member-body {
  height: 650px;
  box-shadow: 0 0 3px rgb(0 0 0 / 10%);
  margin-top: 100px;
}
.content {
  float: left;
}
</style>