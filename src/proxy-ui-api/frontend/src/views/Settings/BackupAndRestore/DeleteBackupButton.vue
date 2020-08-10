<template>
  <small-button
    v-if="canBackup"
    :min_width="50"
    :loading="deleting"
    class="xrd-table-button"
    data-test="backup-delete"
    @click="showConfirmation = true"
    >{{ $t('action.delete') }}
    <confirm-dialog
      :dialog="showConfirmation"
      title="backup.action.delete.dialog.title"
      text="backup.action.delete.dialog.confirmation"
      :data="{ file: backup.filename }"
      @cancel="showConfirmation = false"
      @accept="deleteBackup"
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
import { encodePathParameter } from '@/util/api';
export default Vue.extend({
  name: 'DeleteBackupButton',
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
      showConfirmation: false,
      deleting: false,
    };
  },
  methods: {
    async deleteBackup() {
      this.deleting = true;
      this.showConfirmation = false;
      api
        .remove(`/backups/${encodePathParameter(this.backup.filename)}`)
        .then(() => {
          this.$emit('deleted');
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('backup.action.delete.success', {
              file: this.backup.filename,
            }),
          );
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => (this.deleting = false));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
</style>
