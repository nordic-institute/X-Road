<template>
  <VueApexChart
    type="line"
    :options="options"
    :series="series"
    height="100%"
  ></VueApexChart>
</template>

<script lang="ts" setup>
import VueApexChart from 'vue3-apexcharts';
import { ref } from 'vue';

const props = defineProps<{ series: any }>();

const options = ref({
  xaxis: {
    type: 'datetime',
  },
  stroke: {
    width: 2,
  },
});
</script>
