<template>
  <simpleDialog
    :dialog="dialog"
    :title="title"
    @save="accept"
    @cancel="cancel"
    :cancelButtonText="cancelButtonText"
    :saveButtonText="acceptButtonText"
    :showClose="false"
  >
    <div slot="content">{{$t(text)}}</div>
  </simpleDialog>
</template>

<script lang="ts">
/**
 * A dialog for simple "accept or cancel" functions
 */

import Vue from 'vue';
import SimpleDialog from '@/components/SimpleDialog.vue';

export default Vue.extend({
  components: {
    SimpleDialog,
  },
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

