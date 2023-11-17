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
      :items="backups"
      :search="filter"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="filename"
      :loader-height="2"
      hide-default-footer
    >
      <template #item.buttons="{ item }">
        <div class="cs-table-actions-wrap">
          <xrd-download-backup-button
            :backup-handler="backupHandler"
            :filename="item.filename"
            :can-backup="canBackup"
          />
          <xrd-restore-backup-button
            :backup-handler="backupHandler"
            :filename="item.filename"
            :can-backup="canBackup"
          />
          <xrd-delete-backup-button
            :backup-handler="backupHandler"
            :filename="item.filename"
            :can-backup="canBackup"
            @delete="$emit('delete')"
          />
        </div>
      </template>

      <template #bottom>
        <div class="cs-table-custom-footer" />
      </template>
    </v-data-table>
  </v-card>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { BackupHandler, BackupItem } from '../../types';
import XrdDeleteBackupButton from "./XrdDeleteBackupButton.vue";
import XrdRestoreBackupButton from "./XrdRestoreBackupButton.vue";
import XrdDownloadBackupButton from "./XrdDownloadBackupButton.vue";
import { VDataTable } from "vuetify/labs/VDataTable";

export default defineComponent({
  components: {
    XrdDownloadBackupButton,
    XrdRestoreBackupButton,
    XrdDeleteBackupButton,
    VDataTable
  },
  props:{
    backups: {
      type: Array as PropType<BackupItem[]>,
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
      type: Object as PropType<BackupHandler>,
      required: true,
    },
    canBackup: {
      type: Boolean,
      required: true,
    },
  },
  emits:['delete'],
  data(){
    return {};
  },
  computed: {
    headers() {
      return [
        {
          title: this.$t('global.name') as string,
          key: 'filename',
          align: 'start' as const,
        },
        {
          title: '',
          key: 'buttons',
          align: 'end' as const,
          sortable: false,
        },
      ]
    }
  }
});
</script>

<style lang="scss" scoped>
.cs-table-custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
