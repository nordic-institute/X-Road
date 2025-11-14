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
  <XrdSubView>
    <template #header>
      <div>
        <XrdRoundedSearchField
          v-model="filter"
          data-test="search-query-field"
          width="320"
          :label="$t('action.search')"
        />
      </div>
      <v-spacer />
      <XrdBtn
        v-if="canBackup"
        data-test="backup-create-configuration"
        variant="outlined"
        text="backup.createBackup.button"
        prepend-icon="cloud_upload"
        :loading="creating"
        @click="createBackup"
      />
      <XrdFileUpload
        v-slot="{ upload }"
        :accepts="accepts"
        @file-changed="onFileUploaded"
      >
        <XrdBtn
          v-if="canBackup"
          data-test="backup-upload"
          class="ml-2"
          variant="flat"
          text="backup.uploadBackup.button"
          prepend-icon="upload"
          :loading="uploading"
          @click="upload"
        />
      </XrdFileUpload>
    </template>
    <v-data-table
      item-key="filename"
      class="xrd border"
      hide-default-footer
      :loading="loading"
      :headers="headers"
      :items="backups"
      :search="filter"
      :must-sort="true"
      :items-per-page="-1"
      :loader-height="2"
    >
      <template #item.buttons="{ item }">
        <div class="cs-table-actions-wrap">
          <XrdDownloadBackupButton
            :backup-handler="backupHandler"
            :filename="item.filename"
            :can-backup="canBackup"
          />
          <XrdRestoreBackupButton
            :backup-handler="backupHandler"
            :filename="item.filename"
            :can-backup="canBackup"
          />
          <XrdDeleteBackupButton
            :backup-handler="backupHandler"
            :filename="item.filename"
            :can-backup="canBackup"
            @delete="emit('delete')"
          />
        </div>
      </template>
    </v-data-table>
    <XrdConfirmDialog
      v-if="uploadedFile !== null && needsConfirmation"
      data-test="backup-upload-confirm-overwrite-dialog"
      title="backup.uploadBackup.confirmationDialog.title"
      text="backup.uploadBackup.confirmationDialog.confirmation"
      focus-on-accept
      :data="{ name: uploadedFile.name }"
      :loading="creating"
      @cancel="needsConfirmation = false"
      @accept="overwriteBackup"
    />
  </XrdSubView>
</template>

<script lang="ts" setup>
import { computed, PropType, ref } from 'vue';

import { useI18n } from 'vue-i18n';

import { useRunning, useNotifications } from '../../composables';
import { BackupHandler, BackupItem, FileUploadResult } from '../../types';

import XrdSubView from '../../layouts/XrdSubView.vue';
import { XrdConfirmDialog, XrdBtn } from '../../components';
import XrdFileUpload from '../../components/XrdFileUpload.vue';
import XrdDeleteBackupButton from './XrdDeleteBackupButton.vue';
import XrdDownloadBackupButton from './XrdDownloadBackupButton.vue';
import XrdRestoreBackupButton from './XrdRestoreBackupButton.vue';

const props = defineProps({
  backups: {
    type: Array as PropType<BackupItem[]>,
    required: true,
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
  accepts: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(['delete', 'upload-backup', 'create-backup']);

const { t } = useI18n();
const { addError, addSuccessMessage } = useNotifications();
const { creating, startCreating, stopCreating } = useRunning('creating');
const { uploading, startUploading, stopUploading } = useRunning('uploading');

const headers = computed(() => [
  {
    title: t('global.name') as string,
    key: 'filename',
    align: 'start' as const,
  },
  {
    title: '',
    key: 'buttons',
    align: 'end' as const,
    sortable: false,
  },
]);

const uploadedFile = ref<File | null>(null);
const needsConfirmation = ref(false);
const filter = ref('');

function createBackup() {
  startCreating();
  return props.backupHandler
    .create()
    .then((data) => {
      addSuccessMessage('backup.createBackup.messages.success', {
        file: data.filename,
      });
      if (data.local_conf_present) {
        addError(t('backup.createBackup.messages.localConfWarning'), { warning: true });
      }
    })
    .then(() => emit('create-backup'))
    .catch((error) => addError(error))
    .finally(() => stopCreating());
}

function overwriteBackup(): void {
  startUploading();
  props.backupHandler
    .upload(uploadedFile.value!, true)
    .then(() =>
      addSuccessMessage('backup.uploadBackup.success', {
        file: uploadedFile.value?.name,
      }),
    )
    .then(() => emit('upload-backup'))
    .catch((error) => addError(error))
    .finally(() => {
      stopUploading();
      needsConfirmation.value = false;
    });
}

function onFileUploaded(result: FileUploadResult) {
  startUploading();
  uploadedFile.value = result.file;
  props.backupHandler
    .upload(result.file)
    .then(() =>
      addSuccessMessage('backup.uploadBackup.success', {
        file: uploadedFile.value?.name,
      }),
    )
    .then(() => emit('upload-backup'))
    .catch((error) => {
      const warnings = error.response?.data?.warnings as Array<{
        code: string;
      }>;
      if (error.response?.status === 400 && warnings?.some((warning) => warning.code === 'warning_file_already_exists')) {
        needsConfirmation.value = true;
        return;
      }
      addError(error);
    })
    .finally(() => stopUploading());
}
</script>
