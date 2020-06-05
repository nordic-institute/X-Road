<template>
  <v-progress-linear
    indeterminate
    :height="height"
    :active="active"
  ></v-progress-linear>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    active: {
      type: Boolean,
      default: true,
    },
    height: {
      type: String,
      default: '2px',
    },
  },
});
</script>
