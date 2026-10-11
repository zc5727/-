<template>
  <div class="home">
    <el-card shadow="hover">
      <div class="header">
        <div class="page-header">
          <el-page-header @back="goBack" content="主页">
          </el-page-header>
        </div>
      </div>

      <div style="display: flex;justify-content: left;align-items:center; padding: 20px;">
        <div>
          请选择年份：
        </div>
        <div class="block">
          <el-date-picker
              v-model="year"
              type="year"
              size="mini"
              placeholder="选择年"
              :picker-options="pickerOptions"
              @change="getYear">
          </el-date-picker>
        </div>
      </div>


      <div class="welcome-echarts">
        <div id="histogram" style="width: 50%;height: 400px; background-color:#fff;">

        </div>

        <div id="pie" style="width: 40%;height: 400px; background-color:#fff;">

        </div>

      </div>
    </el-card>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import {getMemberBySeason,getMemberSexByYear} from '@/api/allApi'

export default {
  name: "Index",
  data() {
    return {
      Keyword: '',
      season: [],
      count: [],
      year: '',
      yearNum:2023,
      pickerOptions: {
        disabledDate(time) {
          return time.getFullYear() > new Date().getFullYear();
        }
      }
    }
  },
  mounted() {
    this.drawHistogram()
    this.drawPieCharts()
  },
  methods: {
    goBack() {
      this.$router.back();
    },
    getYear() {
      // 将年份转成数字年
      this.yearNum = this.year.getFullYear();
      this.drawHistogram()
      this.drawPieCharts()
    },
    drawPieCharts() {
      //饼图
      const pie = document.getElementById('pie');
      const pieChart = echarts.init(pie);
      const option = {
        title: {
          text: this.yearNum + '年会员性别比例',
          left: 'center'
        },
        tooltip: {
          trigger: 'item'
        },
        legend: {
          orient: 'vertical',
          left: 'left',
        },
        series: [
          {
            name: '性别',
            type: 'pie',
            radius: '50%',
            data: []
          }
        ]
      };
      getMemberSexByYear({year:this.yearNum}).then(res => {
        option.series[0].data = res.data.sexCount
        pieChart.setOption(option);
      })
    },
    drawHistogram() {
      //柱状图
      const histogram = document.getElementById('histogram');
      const histogramChart = echarts.init(histogram);
      const option = {
        xAxis: {
          name: '季度',
          type: 'category',
          data: []
        },
        yAxis: {
          name: this.yearNum + '年各季度开卡数',
        },
        series: [
          {
            data: [],
            type: 'bar',
            itemStyle: {
              color: function(params) {
                const colors = ['#c23531', '#2f4554', '#61a0a8', '#d48265',];   // 自定义颜色数组
                return colors[params.dataIndex];   // params.dataIndex表示当前柱子在数据数组中的索引值
              }
            },
          }
        ]
      };
      getMemberBySeason({year: this.yearNum}).then(res => {
        option.xAxis.data = res.data.season
        option.series[0].data = res.data.count
        histogramChart.setOption(option);
      })
    }
  },
}
</script>

<style scoped>


.header {
  display: flex;
  justify-content: space-between;
}

.page-header {
  width: 20%;
}

.buttons {
  margin: 0px 0 10px 0;

}

.search {
  margin-right: 10px;
  width: 50%;
  float: left;
}


.chart {
  width: 100%;
  height: 700px;
}

.chart .chart-top {
  height: 200px;
  padding: 17px;
}

.top-card {
  float: left;
  width: 380px;
  height: 130px;
  margin-right: 30px;
  margin-top: 20px;
  border: #00c996 solid 1px;
  border-radius: 5px;
}

.chart .chart-bottom {
  width: 100%;
  height: 500px;
  padding: 30px;
}

.chart .chart-bottom .chart-bottom-card {
  float: left;
  width: 560px;
  height: 400px;
  margin-right: 50px;
  margin-top: 20px;
  border: #00c996 solid 1px;
  border-radius: 5px;
  padding: 30px;
}


.search-input input {
  width: 110px;
}

.welcome-echarts {
  display: flex;
  justify-content: space-between;
  padding: 20px;
}

</style>