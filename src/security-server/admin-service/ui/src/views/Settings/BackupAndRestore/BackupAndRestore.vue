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
  <XrdView data-test="backup-restore-view" title="tab.main.settings">
    <template #tabs>
      <SettingsTabs />
    </template>

    <XrdBackupsDataTable
      accepts=".gpg"
      :can-backup="canBackup"
      :backups="backups"
      :loading="loadingBackups"
      :backup-handler="backupHandler()"
      @delete="fetchData"
      @create-backup="fetchData"
      @upload-backup="fetchData"
    />
  </XrdView>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import { defineComponent } from 'vue';
import { Permissions } from '@/global';
import { Backup } from '@/openapi-types';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { XrdView, useNotifications, BackupHandler, XrdBackupsDataTable } from '@niis/shared-ui';
import SettingsTabs from '@/views/Settings/SettingsTabs.vue';
import { useBackups } from '@/store/modules/backups';

export default defineComponent({
  components: {
    SettingsTabs,
    XrdView,
    XrdBackupsDataTable,
  },
  setup() {
    const { addError } = useNotifications();
    const backupStore = useBackups();
    return { addError, backupStore };
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
    backupHandler(): BackupHandler {
      return {
        create: this.backupStore.createBackup,
        upload: this.backupStore.uploadBackup,
        delete: this.backupStore.deleteBackup,
        download: this.backupStore.downloadBackup,
        restore: this.backupStore.restoreBackup,
      };
    },
    async fetchData() {
      this.loadingBackups = true;
      return this.backupStore.fetchData()
        .then((data) => (this.backups = data))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingBackups = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
