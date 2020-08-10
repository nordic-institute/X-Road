<template>
  <div>
    <LargeButton
      data-test="unregister-client-button"
      @click="confirmUnregisterClient = true"
      outlined
      >{{ $t('action.unregister') }}</LargeButton
    >

    <!-- Confirm dialog for unregister client -->
    <ConfirmDialog
      :dialog="confirmUnregisterClient"
      :loading="unregisterLoading"
      title="client.action.unregister.confirmTitle"
      text="client.action.unregister.confirmText"
      @cancel="confirmUnregisterClient = false"
      @accept="unregisterClient()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    LargeButton,
    ConfirmDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirmUnregisterClient: false as boolean,
      unregisterLoading: false as boolean,
    };
  },

  methods: {
    unregisterClient(): void {
      this.unregisterLoading = true;
      api
        .put(`/clients/${encodePathParameter(this.id)}/unregister`, {})
        .then(
          () => {
            this.$store.dispatch(
              'showSuccess',
              'client.action.unregister.success',
            );
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.$emit('done', this.id);
          this.confirmUnregisterClient = false;
          this.unregisterLoading = false;
        });
    },
  },
});
</script>
