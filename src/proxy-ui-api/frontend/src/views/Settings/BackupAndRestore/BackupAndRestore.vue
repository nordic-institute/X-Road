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
        <large-button
          v-if="canBackup"
          color="primary"
          :loading="creatingBackup"
          data-test="backup-create-configuration"
          @click="createBackup"
          >{{ $t('backup.backupConfiguration.button') }}
        </large-button>
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
    <BackupsDataTable
      :canBackup="canBackup"
      :backups="backups"
      :filter="search"
    />
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import BackupsDataTable from '@/views/Settings/BackupAndRestore/BackupsDataTable.vue';
import { Permissions } from '@/global';
import LargeButton from '@/components/ui/LargeButton.vue';
import * as api from '@/util/api';
import { Backup } from '@/types';
import { AxiosResponse } from 'axios';

export default Vue.extend({
  components: {
    BackupsDataTable,
    LargeButton,
  },
  data() {
    return {
      search: '' as string,
      creatingBackup: false,
      backups: [] as Backup[],
    };
  },
  computed: {
    canBackup(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.BACKUP_CONFIGURATION,
      );
    },
  },
  methods: {
    async fetchData() {
      return api
        .get('/backups')
        .then((res) => {
          this.backups = res.data.sort((a: Backup, b: Backup) => {
            return b.created_at > a.created_at;
          });
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
    async createBackup() {
      this.creatingBackup = true;
      return api
        .post('/backups', null)
        .then((resp: AxiosResponse<Backup>) => {
          this.$bus.$emit(
            'show-success',
            this.$t('backup.backupConfiguration.message.success', {
              file: resp.data.filename,
            }),
          );
          this.fetchData();
        })
        .catch((error) => this.$bus.$emit('show-error', error.message))
        .finally(() => (this.creatingBackup = false));
    },
  },
  created(): void {
    this.fetchData();
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
