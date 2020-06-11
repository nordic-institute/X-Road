<template>
  <simpleDialog
    :dialog="dialog"
    title="warning"
    @save="accept"
    @cancel="cancel"
    :cancelButtonText="cancelButtonText"
    :saveButtonText="acceptButtonText"
    :showClose="false"
    :loading="loading"
  >
    <div slot="content" data-test="dialog-content-text">
      <div v-for="warning in warnings" :key="warning.code">
        <!-- Create the localisation key from warning code -->
        <div class="dlg-warning-header">
          {{ $t(localizationParent + '.' + warning.code) }}
        </div>
        <div v-for="meta in warning.metadata" :key="meta">{{ meta }}</div>
      </div>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
// A dialog for backend warnings
import Vue from 'vue';
import { Prop } from 'vue/types/options';
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
    localizationParent: {
      type: String,
      required: true,
    },
    warnings: {
      type: Array as Prop<string[]>,
      required: true,
    },
    cancelButtonText: {
      type: String,
      default: 'action.cancel',
    },
    acceptButtonText: {
      type: String,
      default: 'action.continue',
    },
    maxWidth: {
      type: String,
      default: '850',
    },
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
