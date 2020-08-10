<template>
  <v-card flat>
    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{ $t('name') }}</th>
          <th></th>
        </tr>
      </thead>
      <template v-if="backups && backups.length > 0">
        <tr v-for="backup in filtered()" v-bind:key="backup.filename">
          <td>{{ backup.filename }}</td>
          <td>
            <div class="d-flex justify-end">
              <small-button
                v-if="canBackup"
                :min_width="50"
                class="xrd-table-button"
                data-test="backup-download"
                @click="downloadBackup(backup.filename)"
                >{{ $t('action.download') }}
              </small-button>
              <restore-backup-button
                :can-backup="canBackup"
                :backup="backup"
                @restored="refreshData"
              />
              <delete-backup-button
                :can-backup="canBackup"
                :backup="backup"
                @deleted="refreshData"
              />
            </div>
          </td>
        </tr>
      </template>
    </table>
  </v-card>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { Backup } from '@/openapi-types';
import { saveResponseAsFile, selectedFilter } from '@/util/helpers';
import SmallButton from '@/components/ui/SmallButton.vue';
import DeleteBackupButton from '@/views/Settings/BackupAndRestore/DeleteBackupButton.vue';
import { Prop } from 'vue/types/options';
import RestoreBackupButton from '@/views/Settings/BackupAndRestore/RestoreBackupButton.vue';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    DeleteBackupButton,
    RestoreBackupButton,
    SmallButton,
  },
  props: {
    filter: {
      type: String,
      default: '',
    },
    canBackup: {
      type: Boolean,
      default: false,
      required: true,
    },
    backups: {
      type: Array as Prop<Backup[]>,
      required: true,
    },
  },
  methods: {
    filtered(): Backup[] {
      return selectedFilter(this.backups, this.filter, 'created_at');
    },
    async downloadBackup(fileName: string) {
      api
        .get(`/backups/${encodePathParameter(fileName)}/download`, {
          responseType: 'blob',
        })
        .then((resp) => saveResponseAsFile(resp, fileName))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    refreshData(): void {
      this.$emit('refresh-data');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
@import '../../../assets/tables';
@import '../../../assets/global-style';
</style>
