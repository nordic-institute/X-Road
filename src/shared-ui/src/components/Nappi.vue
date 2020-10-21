<template>
  <div class="wrapper">
    <v-btn color="red" :disabled="disabled" @click="onClick">
      <v-icon>mdi-heart</v-icon>
      {{ buttonText }}
    </v-btn>
    <slot>default text of slot</slot>
    <v-icon>mdi-help-circle</v-icon>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    disabled: {
      type: Boolean,
      default: false,
    },
    buttonText: {
      type: String,
      required: false,
      default: 'wow!',
    },
  },
  methods: {
    onClick() {
      this.$emit('onClick');
    },
  },
});
</script>
