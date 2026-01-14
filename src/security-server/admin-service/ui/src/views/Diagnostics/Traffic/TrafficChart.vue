<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <v-progress-linear v-if="loading" height="2" indeterminate />
  <VChart
    ref="chartRef" :option="chartOptions"
    height="100%"
    width="100%"
  ></VChart>
</template>

<script lang="ts" setup>
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
} from 'vue';
import VChart from 'vue-echarts';
import { use } from 'echarts/core';
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  ToolboxComponent,
  TooltipComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart } from 'echarts/charts';

use([
  CanvasRenderer,
  LineChart,
  TitleComponent,
  TooltipComponent,
  ToolboxComponent,
  DataZoomComponent,
  LegendComponent,
  GridComponent,
]);

type XaType = 'datetime';

defineProps<{
  series: TrafficSeries[];
  loading?: boolean;
}>();

const chartRef = ref();

onMounted(() => {
  const handleResize = () => {
    if (chartRef.value) {
      chartRef.value.resize();
    }
  };
  window.addEventListener('resize', handleResize);
  chartRef.value._handleResize = handleResize;
});

onBeforeUnmount(() => {
  if (chartRef.value && chartRef.value._handleResize) {
    window.removeEventListener('resize', chartRef.value._handleResize);
  }
});

const chartOptions = computed(() => ({
  grid: {
    left: '1%',
    right: '1%',
    bottom: '15%',
    top: '10%',
  },
  xAxis: {
    type: 'time' as XaType,
    splitNumber: 12,
    axisLabel: {
      formatter: {
        year: '{bold|{yyyy}}',
        month: '{bold|{MMM}}',
        day: '{d} {MMM}',
        hour: '{HH}:{mm}',
        minute: '{HH}:{mm}',
        second: '{HH}:{mm}:{ss}',
      },
      rich: {
        bold: {
          fontWeight: 'bold',
        },
      },
    },
  },
  yAxis: {
    type: 'value',
  },
  tooltip: {
    trigger: 'axis',
  },
  toolbox: {
    feature: {
      dataZoom: {
        yAxisIndex: 'none',
        title: {
          zoom: 'Selection Zoom',
        },
        icon: {
          back: '-',
        },
        emphasis: {
          iconStyle: {
            borderColor: '#1976d2',
            borderWidth: 3,
          },
        },
      },
      myRestore: {
        show: true,
        title: 'Restore',
        icon: 'path://M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
        onclick: function () {
          if (chartRef.value) {
            chartRef.value.dispatchAction({
              type: 'dataZoom',
              start: 0,
              end: 100,
            });
          }
        },
      },
      saveAsImage: {
        icon: 'path://M4 4h16v16H4V4zm2 2v12h12V6H6zm2 10l3-4 2.5 3.5L16 10l2 4H8zm2-6a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
      },
      mySaveAsCSV: {
        show: true,
        title: 'Save as CSV',
        icon: 'path://M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-7 14H8v-2h4v2zm4-4H8v-2h8v2zm0-4H8V7h8v2z',
        onclick: function () {
          downloadAsCSV();
        },
      },
    },
  },
  dataZoom: [
    {
      type: 'inside',
      xAxisIndex: [0],
      filterMode: 'none',
    },
    {
      type: 'slider',
      xAxisIndex: [0],
      filterMode: 'none',
    },
  ],
  legend: {
    data: props.series.map((s) => s.name),
    bottom: 0,
    left: 'center',
  },
  series: props.series.map((s) => ({
    name: s.name,
    type: 'line',
    data: s.data,
    lineStyle: { width: 2, color: s.color },
    itemStyle: { color: s.color },
    showSymbol: false,
    connectNulls: true,
    clip: false,
    zlevel: 1,
  })),
}));

function downloadAsCSV() {
  // Build CSV content
  const option = chartRef.value.getOption();
  const series = option.series;
  let csv = 'Time';
  series.forEach((series: TrafficSeries) => {
    csv += ',' + series.name;
  });
  csv += '\n';
  const dataLength = series[0].data.length;
  for (let i = 0; i < dataLength; i++) {
    csv += series[0].data[i][0]; // timestamp
    series.forEach((series: TrafficSeries) => {
      csv += ',' + series.data[i][1];
    });
    csv += '\n';
  }

  // Download CSV
  const blob = new Blob([csv], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'chart-data.csv';
  a.click();
  URL.revokeObjectURL(url);
}

export type TrafficSeries = {
  name: string;
  color: string;
  data: [number, number][];
};
</script>
