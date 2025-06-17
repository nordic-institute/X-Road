<template>
  <VueApexChart
    type="line"
    :options="options"
    :series="series"
    height="100%"
  ></VueApexChart>
</template>

<script lang="ts" setup>
import { useDate } from 'vuetify';
import { ref } from 'vue';
import VueApexChart from 'vue3-apexcharts';

const props = defineProps<{ series: TrafficSeries[] }>();

const dateAdapter = useDate();

const options = ref({
  xaxis: {
    type: 'datetime',
  },
  tooltip: {
    x: {
      formatter: function (timestamp: number) {
        return dateAdapter.format(new Date(timestamp), 'keyboardDateTime');
      },
    },
  },
  stroke: {
    width: 2,
  },
});

export type TrafficSeries = {
  name: string;
  color: string;
  data: [number, number][];
};
</script>
