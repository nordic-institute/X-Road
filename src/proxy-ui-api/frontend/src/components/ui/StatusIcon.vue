<template>
  <div :class="status"></div>
</template>

<script lang="ts">
/**
 * General purpose component for status icon with color
 */
import Vue from 'vue';
import { Client, ClientStatus } from '@/openapi-types';

export default Vue.extend({
  props: {
    status: {
      type: String,
      validator: (val) =>
        [
          '',
          'green',
          'green-ring',
          'orange',
          'orange-ring',
          'red-ring',
          'red',
        ].includes(val),
    },
  },
});
</script>

<style lang="scss" scoped>
.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

%status-icon-shared {
  height: 8px;
  width: 8px;
  min-height: 8px;
  min-width: 8px;
  border-radius: 50%;
  margin-right: 16px;
}

%status-ring-icon-shared {
  height: 10px;
  width: 10px;
  border-radius: 50%;
  margin-right: 16px;
  border: 2px solid;
}

.red {
  @extend %status-icon-shared;
  background: #d0021b;
}

.red-ring {
  @extend %status-ring-icon-shared;
  border-color: #d0021b;
}

.green {
  @extend %status-icon-shared;
  background: #7ed321;
}

.green-ring {
  @extend %status-ring-icon-shared;
  border-color: #7ed321;
}

.orange {
  @extend %status-icon-shared;
  border-color: #f5a623;
}

.orange-ring {
  @extend %status-ring-icon-shared;
  border-color: #f5a623;
}
</style>
