<template>
  <div class="wrapper xrd-view-common">
    <div class="table-toolbar">
      <v-text-field
        v-model="search"
        :label="$t('action.search')"
        single-line
        hide-details
        class="search-input"
        data-test="backup-search"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
      <div>
        <v-btn
          v-if="canBackup"
          color="primary"
          rounded
          dark
          class="button-spacing rounded-button elevation-0"
          data-test="backup-create-configuration"
          >{{ $t('backup.backupConfiguration') }}
        </v-btn>
        <v-btn
          v-if="canBackup"
          color="primary"
          rounded
          dark
          class="button-spacing rounded-button elevation-0"
          data-test="backup-upload"
          >{{ $t('backup.uploadBackup') }}
        </v-btn>
      </div>
    </div>
    <BackupsDataTable :canBackup="canBackup" :filter="search" />
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab. Not implemented yet.
 */
import Vue from 'vue';
import BackupsDataTable from '@/views/Settings/BackupAndRestore/BackupsDataTable.vue';
import { Permissions } from '@/global';

export default Vue.extend({
  components: {
    BackupsDataTable,
  },
  data() {
    return {
      search: '' as string,
    };
  },
  computed: {
    canBackup(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.BACKUP_CONFIGURATION,
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
.search-input {
  max-width: 300px;
}

.button-spacing {
  margin-left: 20px;
}
</style>
