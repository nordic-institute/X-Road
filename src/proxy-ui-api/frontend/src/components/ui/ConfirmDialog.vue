<template>
  <simpleDialog
    :dialog="dialog"
    :title="title"
    @save="accept"
    @cancel="cancel"
    :cancelButtonText="cancelButtonText"
    :saveButtonText="acceptButtonText"
    :showClose="false"
    :loading="loading"
  >
    <div slot="content" data-test="dialog-content-text">
      {{ $t(text, data) }}
    </div>
  </simpleDialog>
</template>

<script lang="ts">
/**
 * A dialog for simple "accept or cancel" functions
 */

import Vue from 'vue';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';

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
    // Set save button loading spinner
    loading: {
      type: Boolean,
      default: false,
    },
    // In case the confirmation text requires additional data
    data: {
      type: Object,
      required: false,
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
