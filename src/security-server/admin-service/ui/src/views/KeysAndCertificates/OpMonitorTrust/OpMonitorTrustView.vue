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
  <XrdView data-test="op-monitor-trust-view" title="tab.keys.opMonitorTrust" :loading="loading || loadingOpMonitorCa">
    <template #tabs>
      <KeysAndCertificatesTabs />
    </template>
    <XrdSubView>
      <XrdCard title="opMonitorTrust.externalRpcCa.title">
        <template v-if="canDownload" #title-actions>
          <XrdBtn
            data-test="download-external-rpc-ca-button"
            variant="outlined"
            prepend-icon="download"
            text="opMonitorTrust.externalRpcCa.download"
            :loading="downloading"
            :disabled="!externalRpcCa"
            @click="download"
          />
        </template>
        <p class="body-regular px-4 pb-4" data-test="external-rpc-ca-description">
          {{ $t('opMonitorTrust.externalRpcCa.description') }}
        </p>
        <p v-if="unavailable" class="body-regular px-4 pb-4 opacity-60" data-test="external-rpc-ca-unavailable">
          {{ $t('opMonitorTrust.externalRpcCa.unavailable') }}
        </p>
        <v-table v-if="externalRpcCa" class="xrd bg-surface-container">
          <tbody>
            <tr>
              <td>
                <XrdLabelWithIcon
                  data-test="external-rpc-ca-hash"
                  icon="editor_choice"
                  :label="externalRpcCa.hash"
                  clickable
                  @navigate="router.push({ name: RouteName.ExternalRpcCaDetails })"
                >
                  <template #label>
                    <XrdHashValue :value="externalRpcCa.hash" />
                  </template>
                </XrdLabelWithIcon>
              </td>
            </tr>
          </tbody>
        </v-table>
      </XrdCard>
      <XrdCard v-if="canViewOpMonitorCa" class="mt-6" title="opMonitorTrust.opMonitorCa.title">
        <template v-if="canUploadOpMonitorCa || canDeleteOpMonitorCa" #title-actions>
          <div class="d-flex flex-row align-center justify-end">
            <XrdBtn
              v-if="canDeleteOpMonitorCa && opMonitorCa"
              data-test="delete-op-monitor-ca-button"
              variant="outlined"
              prepend-icon="delete"
              text="opMonitorTrust.opMonitorCa.delete"
              class="mr-2"
              @click="confirmDeleteOpMonitorCa = true"
            />
            <XrdFileUpload
              v-if="canUploadOpMonitorCa"
              v-slot="{ upload }"
              accepts=".der, .crt, .pem, .cer"
              @file-changed="uploadOpMonitorCaFile"
            >
              <XrdBtn
                data-test="upload-op-monitor-ca-button"
                variant="outlined"
                prepend-icon="upload"
                :text="opMonitorCa ? 'opMonitorTrust.opMonitorCa.replace' : 'opMonitorTrust.opMonitorCa.upload'"
                :loading="uploading"
                :disabled="loadingOpMonitorCa"
                @click="upload"
              />
            </XrdFileUpload>
          </div>
        </template>
        <p class="body-regular px-4 pb-4" data-test="op-monitor-ca-description">{{ $t('opMonitorTrust.opMonitorCa.description') }}</p>
        <p class="body-regular px-4 pb-4" data-test="op-monitor-ca-restart-hint">{{ $t('opMonitorTrust.opMonitorCa.restartHint') }}</p>
        <p v-if="!opMonitorCa && !loadingOpMonitorCa" class="body-regular px-4 pb-4 opacity-60" data-test="op-monitor-ca-empty">
          {{ $t('opMonitorTrust.opMonitorCa.empty') }}
        </p>
        <v-table v-if="opMonitorCa" class="xrd bg-surface-container">
          <tbody>
            <tr>
              <td>
                <XrdLabelWithIcon
                  data-test="op-monitor-ca-hash"
                  icon="editor_choice"
                  :label="opMonitorCa.hash"
                  clickable
                  @navigate="router.push({ name: RouteName.OpMonitorCaDetails })"
                >
                  <template #label>
                    <XrdHashValue :value="opMonitorCa.hash" />
                  </template>
                </XrdLabelWithIcon>
              </td>
            </tr>
          </tbody>
        </v-table>
      </XrdCard>
      <XrdConfirmDialog
        v-if="confirmDeleteOpMonitorCa"
        data-test="delete-op-monitor-ca-confirm-dialog"
        title="opMonitorTrust.opMonitorCa.deleteConfirmTitle"
        text="opMonitorTrust.opMonitorCa.deleteConfirmText"
        :loading="deleting"
        @cancel="confirmDeleteOpMonitorCa = false"
        @accept="deleteOpMonitorCaConfirmed"
      />
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  FileUploadResult,
  XrdBtn,
  XrdCard,
  XrdConfirmDialog,
  XrdFileUpload,
  XrdHashValue,
  XrdLabelWithIcon,
  XrdSubView,
  XrdView,
  useNotifications,
} from '@niis/shared-ui';
import { CertificateDetails } from '@/openapi-types';
import { Permissions, RouteName } from '@/global';
import { useOpMonitorTrust } from '@/store/modules/op-monitor-trust';
import { useUser } from '@/store/modules/user';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';

const router = useRouter();
const { hasPermission } = useUser();
const { fetchExternalRpcCa, downloadExternalRpcCa, fetchOpMonitorCa, uploadOpMonitorCa, deleteOpMonitorCa } = useOpMonitorTrust();
const { addError, addSuccessMessage } = useNotifications();

const externalRpcCa = ref<CertificateDetails | undefined>(undefined);
const loading = ref(false);
const unavailable = ref(false);
const downloading = ref(false);
const opMonitorCa = ref<CertificateDetails | undefined>(undefined);
const loadingOpMonitorCa = ref(false);
const uploading = ref(false);
const confirmDeleteOpMonitorCa = ref(false);
const deleting = ref(false);

const canDownload = computed(() => hasPermission(Permissions.DOWNLOAD_EXTERNAL_RPC_CA));
const canViewOpMonitorCa = computed(() => hasPermission(Permissions.VIEW_OP_MONITOR_CA));
const canUploadOpMonitorCa = computed(() => hasPermission(Permissions.UPLOAD_OP_MONITOR_CA));
const canDeleteOpMonitorCa = computed(() => hasPermission(Permissions.DELETE_OP_MONITOR_CA));

function fetchData(): void {
  loading.value = true;
  fetchExternalRpcCa()
    .then((details) => (externalRpcCa.value = details))
    .catch((error) => {
      unavailable.value = true;
      if (error?.response?.status !== 404) {
        addError(error);
      }
    })
    .finally(() => (loading.value = false));
}

function download(): void {
  downloading.value = true;
  downloadExternalRpcCa()
    .catch((error) => addError(error))
    .finally(() => (downloading.value = false));
}

function fetchOpMonitorCaData(): void {
  loadingOpMonitorCa.value = true;
  fetchOpMonitorCa()
    .then((details) => (opMonitorCa.value = details))
    .catch((error) => addError(error))
    .finally(() => (loadingOpMonitorCa.value = false));
}

function uploadOpMonitorCaFile(event: FileUploadResult): void {
  uploading.value = true;
  uploadOpMonitorCa(event.file)
    .then((details) => {
      opMonitorCa.value = details;
      addSuccessMessage('opMonitorTrust.opMonitorCa.uploaded');
    })
    .catch((error) => addError(error))
    .finally(() => (uploading.value = false));
}

function deleteOpMonitorCaConfirmed(): void {
  deleting.value = true;
  deleteOpMonitorCa()
    .then(() => {
      opMonitorCa.value = undefined;
      confirmDeleteOpMonitorCa.value = false;
      addSuccessMessage('opMonitorTrust.opMonitorCa.deleted');
    })
    .catch((error) => addError(error))
    .finally(() => (deleting.value = false));
}

fetchData();
if (canViewOpMonitorCa.value) {
  fetchOpMonitorCaData();
}
</script>

<style lang="scss" scoped></style>
