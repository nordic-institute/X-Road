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
    <xrd-button
      v-if="canBackup"
      data-test="backup-create-configuration"
      color="primary"
      outlined
      :loading="creating"
      @click="createBackup"
    >
      <v-icon class="xrd-large-button-icon" icon="icon-Database-backup" />
      {{ $t('backup.createBackup.button') }}
    </xrd-button>
    <xrd-file-upload
      v-slot="{ upload }"
      :accepts="accepts"
      @file-changed="onFileUploaded"
    >
      <xrd-button
        v-if="canBackup"
        color="primary"
        :loading="uploading"
        class="button-spacing"
        data-test="backup-upload"
        @click="upload"
      >
        <v-icon class="xrd-large-button-icon" icon="icon-Upload" />

        {{ $t('backup.uploadBackup.button') }}
      </xrd-button>
    </xrd-file-upload>
    <xrd-confirm-dialog
      v-if="uploadedFile !== null && needsConfirmation"
      data-test="backup-upload-confirm-overwrite-dialog"
      title="backup.uploadBackup.confirmationDialog.title"
      text="backup.uploadBackup.confirmationDialog.confirmation"
      :data="{ name: uploadedFile.name }"
      :loading="uploading"
      @cancel="needsConfirmation = false"
      @accept="overwriteBackup"
    />
  </div>
</template>

<script lang="ts">
import { BackupHandler, FileUploadResult } from '../../types';
import { defineComponent, PropType } from 'vue';
import XrdButton from "../XrdButton.vue";
import XrdConfirmDialog from "../XrdConfirmDialog.vue";
import XrdFileUpload from "../XrdFileUpload.vue";


export default defineComponent({
  components: {
    XrdButton,
    XrdConfirmDialog,
    XrdFileUpload
  },
  props: {
    accepts: {
      type: String,
      required: true,
    },
    canBackup: {
      type: Boolean,
      required: true,
    },
    backupHandler: {
      type: Object as PropType<BackupHandler>,
      required: true,
    },
  },
  emits: ['create-backup', 'upload-backup'],
  data() {
    return {
      creating: false,
      uploading: false,
      needsConfirmation: false,
      uploadedFile: null as File | null
    };
  },
  methods: {
    createBackup() {
      this.creating = true;
      return this.backupHandler
        .create()
        .then((data) => {
          this.backupHandler.showSuccess('backup.createBackup.messages.success', {
            file: data.filename,
          });
          if (data.local_conf_present) {
            this.backupHandler.showWarning(
              'backup.createBackup.messages.localConfWarning',
            );
          }
        })
        .then(() => this.$emit('create-backup'))
        .catch((error) => this.backupHandler.showError(error))
        .finally(() => (this.creating = false));
    },
    onFileUploaded(result: FileUploadResult) {
    this.uploading = true;
    this.uploadedFile = result.file;
    this.backupHandler
      .upload(result.file)
      .then(() =>
        this.backupHandler.showSuccess('backup.uploadBackup.success', {
          file: this.uploadedFile?.name,
        }),
      )
      .then(() => this.$emit('upload-backup'))
      .catch((error) => {
        const warnings = error.response?.data?.warnings as Array<{
          code: string;
        }>;
        if (
          error.response?.status === 400 &&
          warnings?.some(
            (warning) => warning.code === 'warning_file_already_exists',
          )
        ) {
          this.needsConfirmation = true;
          return;
        }
        this.backupHandler.showError(error);
      })
      .finally(() => (this.uploading = false));
  },
  overwriteBackup(): void {
    this.uploading = true;
    this.backupHandler
      // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
      .upload(this.uploadedFile!, true)
      .then(() =>
        this.backupHandler.showSuccess('backup.uploadBackup.success', {
          file: this.uploadedFile?.name,
        }),
      )
      .then(() => this.$emit('upload-backup'))
      .catch((error) => this.backupHandler.showError(error))
      .finally(() => {
        this.uploading = false;
        this.needsConfirmation = false;
      });
  }
  }
});
</script>

<style lang="scss" scoped>
.button-spacing {
  margin-left: 20px;
}

// For adjusting the position of an icon when used inside a button component
.xrd-large-button-icon {
  margin-right: 5px;
  margin-left: -2px;
  min-width: 20px; // needs a width or the buttons with short text "collapse"
}
</style>
