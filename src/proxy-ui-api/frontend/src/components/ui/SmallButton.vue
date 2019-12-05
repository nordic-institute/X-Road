<template>
  <v-btn
    small
    :outlined="outlined"
    :disabled="disabled"
    :min-width="min_width"
    :loading="loading"
    rounded
    color="primary"
    class="xrd-small-button"
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
      default: true,
    },
    // Set button disabled state
    disabled: {
      type: Boolean,
      default: false,
    },
    // Show loading spinner
    loading: {
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

<style lang="scss" scoped>
</style>
