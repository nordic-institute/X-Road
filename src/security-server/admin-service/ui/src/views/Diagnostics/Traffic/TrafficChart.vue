<template>
  <VueApexChart
    type="line"
    :options="options"
    :series="series"
    height="400px"
  ></VueApexChart>
</template>

<script lang="ts" setup>
import VueApexChart from 'vue3-apexcharts';
import { ref } from 'vue';

const options = ref({
  xaxis: {
    type: 'datetime',
  },
});
const series = ref([
  {
    name: 'Successful requests',
    color: '#00d54d',
    data: generateRandomData(40, 100),
  },
  {
    name: 'Failed requests',
    color: '#d84957',
    data: generateRandomData(0, 5),
  },
]);

function generateRandomData(min = 0, max = 100) {
  const data = [];
  const now = new Date().getTime();
  let date = new Date(now - 7 * 24 * 60 * 60 * 1000).getTime();
  do {
    date = new Date(date + 60 * 60 * 1000).getTime();
    data.push([date, min + Math.floor(Math.random() * (max - min))]);
  } while (date < now);
  return data;
}
</script>
