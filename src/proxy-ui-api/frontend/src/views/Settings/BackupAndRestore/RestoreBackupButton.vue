<template>
  <small-button
    v-if="canBackup"
    :min_width="50"
    class="xrd-table-button"
    data-test="backup-restore"
    @click="showConfirmation = true"
    >{{ $t('action.restore') }}
    <confirm-dialog
      :dialog="showConfirmation"
      :loading="restoring"
      title="backup.action.restore.dialog.title"
      text="backup.action.restore.dialog.confirmation"
      :data="{ file: backup.filename }"
      @cancel="showConfirmation = false"
      @accept="restoreBackup"
    />
  </small-button>
</template>

<script lang="ts">
import Vue from 'vue';
import { Prop } from 'vue/types/options';
import { Backup } from '@/openapi-types';
import * as api from '@/util/api';
import SmallButton from '@/components/ui/SmallButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
export default Vue.extend({
  name: 'RestoreBackupButton',
  components: {
    SmallButton,
    ConfirmDialog,
  },
  props: {
    canBackup: {
      type: Boolean,
      default: false,
      required: true,
    },
    backup: {
      type: Object as Prop<Backup>,
      required: true,
    },
  },
  data() {
    return {
      showConfirmation: false as boolean,
      restoring: false as boolean,
    };
  },
  methods: {
    restoreBackup() {
      this.restoring = true;
      api
        .put(`/backups/${this.backup.filename}/restore`, {})
        .then(() => {
          this.$emit('restored');
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('backup.action.restore.success', {
              file: this.backup.filename,
            }),
          );
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => {
          this.showConfirmation = false;
          this.restoring = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
</style>
