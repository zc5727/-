<template>
  <div class="rechargeRecord">
    <el-table
        :data="tableData"
        stripe
        height="520"
        style="width: 100%">
      <el-table-column
          prop="rechargeDate"
          label="充值时间">
      </el-table-column>
      <el-table-column
          prop="rechargeMoney"
          label="金额">
      </el-table-column>
      <el-table-column
          prop="rechargeMethod"
          label="方式">
      </el-table-column>
      <el-table-column
          prop="rechargeStatus"
          label="状态">
        <template slot-scope="scope">
          <span v-if="scope.row.rechargeStatus == '1'">充值成功</span>
          <span v-else>充值失败</span>
        </template>
      </el-table-column>
    </el-table>
  </div>

</template>

<script>
import {getAllMember, getRechargeByMemberNo, totalMember} from "@/api/allApi";

export default {
  data() {
    return {
      tableData: [],
      admin: {

      },
    }
  },
  mounted() {
    let _this = this
    getRechargeByMemberNo({
      memberNo:_this.admin.memberNo
    }).then(res=>{
      this.tableData = res.data
    }).catch(err=>{
      console.log(err.message)
    })
  },
  created() {
    this.admin = JSON.parse(window.localStorage.getItem('access-member'))
  },
  methods:{

  }
}
</script>
<style scoped>
.rechargeRecord {
  padding: 40px;
  width: 1000px;
}
</style>