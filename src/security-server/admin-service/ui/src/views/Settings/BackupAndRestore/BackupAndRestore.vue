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
  <div data-test="backup-restore-view" class="xrd-view-common">
    <div class="xrd-table-toolbar mt-0 pl-0">
      <div class="xrd-title-search">
        <div class="xrd-view-title">
          {{ $t('tab.settings.backupAndRestore') }}
        </div>
        <xrd-search v-model="search" />
      </div>
      <xrd-backups-toolbar
        accepts=".gpg"
        :can-backup="canBackup"
        :backup-handler="backupHandler()"
        @create-backup="fetchData"
        @upload-backup="fetchData"
      />
    </div>
    <xrd-backups-data-table
      :can-backup="canBackup"
      :backups="backups"
      :filter="search"
      :loading="loadingBackups"
      :backup-handler="backupHandler()"
      @delete="fetchData"
    />
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import { defineComponent } from 'vue';
import { Permissions } from '@/global';
import * as api from '@/util/api';
import { Backup, BackupExt } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { encodePathParameter } from '@/util/api';
import { saveResponseAsFile } from '@/util/helpers';
import {
  BackupHandler,
  BackupItem,
  XrdBackupsToolbar,
  XrdBackupsDataTable,
} from '@niis/shared-ui';

const uploadBackup = (backupFile: File, ignoreWarnings = false) => {
  const formData = new FormData();
  formData.set('file', backupFile, backupFile.name);
  return api
    .post<BackupItem>(
      `/backups/upload?ignore_warnings=${ignoreWarnings}`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      },
    )
    .then((resp) => resp.data);
};

export default defineComponent({
  components: {
    XrdBackupsToolbar,
    XrdBackupsDataTable,
  },
  data() {
    return {
      search: '' as string,
      creatingBackup: false,
      uploadingBackup: false,
      needsConfirmation: false,
      uploadedFile: null as File | null,
      backups: [] as Backup[],
      loadingBackups: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    canBackup(): boolean {
      return this.hasPermission(Permissions.BACKUP_CONFIGURATION);
    },
  },
  created(): void {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, [
      'showError',
      'showSuccess',
      'showWarningMessage',
    ]),
    backupHandler(): BackupHandler {
      return {
        showError: this.showError,
        showSuccess: this.displaySuccess,
        showWarning: this.displayWarning,
        create: this.createBackup,
        upload: uploadBackup,
        delete: this.deleteBackup,
        download: this.downloadBackup,
        restore: this.restoreBackup,
      };
    },
    fetchData() {
      this.loadingBackups = true;
      return api
        .get<Backup[]>('/backups')
        .then((res) => {
          this.backups = res.data.sort((a, b) => {
            return b.created_at.localeCompare(a.created_at);
          });
        })
        .catch((error) => this.showError(error))
        .finally(() => (this.loadingBackups = false));
    },
    createBackup() {
      this.creatingBackup = true;
      return api
        .post<BackupExt>('/backups/ext', null)
        .then((resp) => resp.data);
    },
    deleteBackup(filename: string) {
      return api.remove(`/backups/${encodePathParameter(filename)}`);
    },
    downloadBackup(fileName: string) {
      return api
        .get(`/backups/${encodePathParameter(fileName)}/download`, {
          responseType: 'blob',
        })
        .then((resp) => saveResponseAsFile(resp, fileName));
    },
    restoreBackup(fileName: string) {
      return api.put(`/backups/${encodePathParameter(fileName)}/restore`, {});
    },
    displaySuccess(textKey: string, data: Record<string, unknown> = {}) {
      this.showSuccess(this.$t(textKey, data));
    },
    displayWarning(textKey: string, data: Record<string, unknown> = {}) {
      this.showWarningMessage(this.$t(textKey, data));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';
</style>
