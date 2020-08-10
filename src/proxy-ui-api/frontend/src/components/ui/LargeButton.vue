<template>
  <v-btn
    :outlined="outlined"
    :disabled="disabled"
    :min-width="min_width"
    :loading="loading"
    v-if="isAllowed"
    rounded
    color="primary"
    class="large-button"
    @click="click"
  >
    <slot></slot>
  </v-btn>
</template>

<script lang="ts">
/** Wrapper for vuetify button with x-road look */

import Vue, { PropType } from 'vue';
import { Permissions } from '@/global';

export default Vue.extend({
  props: {
    outlined: {
      type: Boolean,
      default: false,
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
    requiresPermission: {
      required: false,
      type: String as PropType<Permissions | undefined>,
      validator: (val) =>
        Object.values(Permissions).some((permission) => permission === val),
    },
  },
  computed: {
    isAllowed(): boolean {
      return (
        this.requiresPermission === undefined ||
        this.$store.getters.hasPermission(this.requiresPermission)
      );
    },
  },
  methods: {
    click(event: MouseEvent): void {
      this.$emit('click', event);
    },
  },
});
</script>

<style lang="scss" scoped>
$large-button-width: 140px;

.large-button {
  min-width: $large-button-width !important;
  border-radius: 4px;
  text-transform: uppercase;
  background-color: white;
}
</style>
