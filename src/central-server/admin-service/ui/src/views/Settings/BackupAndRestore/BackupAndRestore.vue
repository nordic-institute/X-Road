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
  <div data-test="backup-and-restore-view">
    <div class="table-toolbar mt-0 pl-0">
      <div class="xrd-title-search">
        <div class="xrd-view-title">
          {{ $t('tab.settings.backupAndRestore') }}
        </div>
        <xrd-search v-model="filter" />
      </div>
      <xrd-backups-toolbar
        accepts=".gpg"
        :backup-handler="backupHandler()"
        :can-backup="canBackup"
        @refresh-backups="fetchBackups"
      />
    </div>
    <xrd-backups-data-table
      :filter="filter"
      :backups="backups"
      :loading="loading"
      :can-backup="canBackup"
      :backup-handler="backupHandler()"
      @refresh-backups="fetchBackups"
    />
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import { Colors, Permissions } from '@/global';
import { mapActions, mapState, mapStores } from 'pinia';
import { useBackupsStore } from '@/store/modules/backups';
import { notificationsStore } from '@/store/modules/notifications';
import VueI18n from 'vue-i18n';
import Values = VueI18n.Values;
import { Backup } from '@/openapi-types';
import { userStore } from '@/store/modules/user';

export default Vue.extend({
  data() {
    return {
      filter: '',
      loading: false,
      colors: Colors,
    };
  },
  computed: {
    ...mapStores(useBackupsStore),
    ...mapState(userStore, ['hasPermission']),
    canBackup(): boolean {
      return this.hasPermission(Permissions.BACKUP_CONFIGURATION);
    },
    backups(): Backup[] {
      return this.backupStore.backups;
    },
  },
  created() {
    this.fetchBackups();
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    backupHandler() {
      return {
        showSuccess: this.displaySuccess,
        showError: this.showError,
        delete: this.deleteBackup,
        download: this.downloadBackup,
        upload: this.uploadBackup,
        create: this.createBackup,
        restore: this.restoreFromBackup,
      };
    },
    fetchBackups() {
      this.loading = true;
      this.backupStore
        .getBackups()
        .catch((error) => this.showError(error))
        .finally(() => (this.loading = false));
    },
    createBackup() {
      return this.backupStore.createBackup().then((resp) => resp.data);
    },
    deleteBackup(filename: string) {
      return this.backupStore.deleteBackup(filename);
    },
    restoreFromBackup(filename: string) {
      return this.backupStore.restoreFromBackup(filename);
    },
    downloadBackup(filename: string) {
      return this.backupStore.downloadBackup(filename);
    },
    uploadBackup(backupFile: File, ignoreWarnings = false) {
      return this.backupStore.uploadBackup(backupFile, ignoreWarnings);
    },
    displaySuccess(textKey: string, data: Values = {}) {
      this.showSuccess(this.$t(textKey, data));
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';
</style>
