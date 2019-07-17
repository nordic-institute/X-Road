<template>
  <v-dialog :value="dialog" persistent :max-width="maxWidth">
    <v-card>
      <v-card-title class="headline">{{$t(title)}}</v-card-title>
      <v-card-text>{{$t(text)}}</v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" flat @click="cancel()">{{$t(cancelButtonText)}}</v-btn>
        <v-btn color="primary" flat @click="accept()">{{$t(acceptButtonText)}}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
/**
 * A dialog for simple "accept or cancel" functions
 */

import Vue from 'vue';

export default Vue.extend({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
    text: {
      type: String,
      required: true,
    },
    cancelButtonText: {
      type: String,
      default: 'action.cancel',
    },
    acceptButtonText: {
      type: String,
      default: 'action.yes',
    },
    maxWidth: {
      type: String,
      default: '290px',
    },
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    accept(): void {
      this.$emit('accept');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';
</style>

