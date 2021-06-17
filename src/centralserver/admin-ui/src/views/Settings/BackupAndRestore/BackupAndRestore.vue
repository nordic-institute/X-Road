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
  <div>
    <div class="table-toolbar mt-0 pl-0">
      <div class="xrd-title-search">
        <div class="xrd-view-title">
          {{ $t('tab.settings.backupAndRestore') }}
        </div>
        <xrd-search v-model="search" />
      </div>
      <div>
        <xrd-button
          color="primary"
          outlined
          :loading="creatingBackup"
          data-test="backup-create-configuration"
          @click="createBackup"
        >
          <v-icon class="xrd-large-button-icon">icon-Database-backup</v-icon
          >{{ $t('backup.backupConfiguration.button') }}
        </xrd-button>
        <xrd-file-upload
          v-slot="{ upload }"
          accepts=".tar"
          @file-changed="onFileUploaded"
        >
          <xrd-button
            color="primary"
            :loading="uploadingBackup"
            class="ml-5"
            data-test="backup-upload"
            @click="upload"
          >
            <v-icon class="xrd-large-button-icon">icon-Upload</v-icon>

            {{ $t('backup.uploadBackup.button') }}
          </xrd-button>
        </xrd-file-upload>
        <xrd-confirm-dialog
          v-if="uploadedFile !== null"
          :dialog="needsConfirmation"
          title="backup.uploadBackup.confirmationDialog.title"
          data-test="backup-upload-confirm-overwrite-dialog"
          text="backup.uploadBackup.confirmationDialog.confirmation"
          :data="{ name: uploadedFile.name }"
          :loading="uploadingBackup"
          @cancel="needsConfirmation = false"
          @accept="overwriteBackup"
        />
      </div>
    </div>
    <BackupsDataTable />
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import BackupsDataTable from '@/views/Settings/BackupAndRestore/BackupsDataTable.vue';
import { FileUploadResult } from '@niis/shared-ui';

export default Vue.extend({
  components: {
    BackupsDataTable,
  },
  data() {
    return {
      search: '' as string,
      creatingBackup: false,
      uploadingBackup: false,
      needsConfirmation: false,
      uploadedFile: null as File | null,
    };
  },
  methods: {
    async createBackup() {
      this.creatingBackup = true;
      await new Promise<void>((res) => {
        setTimeout(() => res(), 1000);
      });
      this.creatingBackup = false;
    },
    // This linter disable can be removed when actually implementing the function
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    async onFileUploaded(result: FileUploadResult) {
      this.uploadingBackup = true;
      this.uploadedFile = new File([], 'file name');
      await new Promise<void>((res) => {
        setTimeout(() => res(), 1000);
      });
      this.uploadingBackup = false;
    },
    async overwriteBackup() {
      this.uploadingBackup = true;
      await new Promise<void>((res) => {
        setTimeout(() => res(), 1000);
      });
      this.uploadingBackup = false;
    },
  },
});
</script>
<style lang="scss">
@import '~styles/tables';
</style>
