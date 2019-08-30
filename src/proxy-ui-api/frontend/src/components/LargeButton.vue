<template>
  <v-btn
    :outlined="outlined"
    :disabled="disabled"
    :min-width="min_width"
    round
    color="primary"
    class="xrd-big-button"
    @click="click()"
  >
    <slot></slot>
  </v-btn>
</template>


<script lang="ts">
/** Wrapper for vuetify button with x-road look */

import Vue from 'vue';

export default Vue.extend({
  props: {
    outlined: {
      type: Boolean,
      default: false,
    },
    disabled: {
      type: Boolean,
      default: false,
    },
    min_width: {
      type: Number,
      default: 120,
    },
  },

  methods: {
    click(event: any): void {
      this.$emit('click', event);
    },
  },
});
</script>

<style>
</style>