<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <v-card flat>
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="filteredBackups"
      :search="filter"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.button`]="{ item }">
        <div class="cs-table-actions-wrap">
          <download-backup-button
            :filename="item.filename"
            :can-backup="canBackup"
            :backup-handler="backupHandler"
          />
          <restore-backup-button
            :filename="item.filename"
            :can-backup="canBackup"
            :backup-handler="backupHandler"
            @refresh-backups="$emit('refresh-backups')"
          />
          <delete-backup-button
            :filename="item.filename"
            :can-backup="canBackup"
            :backup-handler="backupHandler"
            @refresh-backups="$emit('refresh-backups')"
          />
        </div>
      </template>

      <template #footer>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>
  </v-card>
</template>

<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import DeleteBackupButton from './DeleteBackupButton.vue';
import RestoreBackupButton from './RestoreBackupButton.vue';
import DownloadBackupButton from './DownloadBackupButton.vue';
import { BackupHandler, BackupItem } from './backup-handler';
import { Prop } from 'vue/types/options';

export default Vue.extend({
  components: {
    DownloadBackupButton,
    DeleteBackupButton,
    RestoreBackupButton,
  },
  props: {
    backups: {
      type: Array as Prop<BackupItem[]>,
      required: true,
    },
    filter: {
      type: String,
      default: '',
    },
    loading: {
      type: Boolean,
      required: true,
    },
    backupHandler: {
      type: Object as Prop<BackupHandler>,
      required: true,
    },
    canBackup: {
      type: Boolean,
      required: true,
    },
  },
  computed: {
    filteredBackups(): BackupItem[] {
      return this.backups;
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('global.name') as string,
          align: 'start',
          value: 'filename',
          class: 'xrd-table-header backups-table-header-name',
        },
        {
          text: '',
          align: 'end',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header backups-table-header-buttons',
        },
      ];
    },
  },
});
</script>

<style lang="scss" scoped></style>
