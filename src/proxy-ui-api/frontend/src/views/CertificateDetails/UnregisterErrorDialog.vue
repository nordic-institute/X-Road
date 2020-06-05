<template>
  <SimpleDialog
    :dialog="dialog"
    title="warning"
    @save="accept"
    @cancel="cancel"
    :cancelButtonText="cancelButtonText"
    :saveButtonText="acceptButtonText"
    :showClose="false"
  >
    <div slot="content" data-test="dialog-content-text">
      <template v-if="errorResponse">{{ $t('keys.unregisterError') }}</template>
    </div>
  </SimpleDialog>
</template>

<script lang="ts">
/**
 * A dialog for handling error in unregister auth certificate
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

    errorResponse: {
      type: Object,
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
@import '../../assets/dialogs';
</style>
