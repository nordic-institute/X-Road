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
      <XrdCard title="opMonitorTrust.internalRpcCa.title">
        <template v-if="canDownload" #title-actions>
          <XrdBtn
            data-test="download-internal-rpc-ca-button"
            variant="outlined"
            prepend-icon="download"
            text="opMonitorTrust.internalRpcCa.download"
            :loading="downloading"
            :disabled="!internalRpcCa"
            @click="download"
          />
        </template>
        <p class="body-regular px-4 pb-4" data-test="internal-rpc-ca-description">{{ $t('opMonitorTrust.internalRpcCa.description') }}</p>
        <p v-if="unavailable" class="body-regular px-4 pb-4 opacity-60" data-test="internal-rpc-ca-unavailable">
          {{ $t('opMonitorTrust.internalRpcCa.unavailable') }}
        </p>
        <CaCertificateDetails v-if="internalRpcCa" :certificate="internalRpcCa" test-id-prefix="internal-rpc-ca" />
      </XrdCard>
      <XrdCard v-if="canViewOpMonitorCa" class="mt-6" title="opMonitorTrust.opMonitorCa.title">
        <template v-if="canUploadOpMonitorCa" #title-actions>
          <XrdFileUpload v-slot="{ upload }" accepts=".der, .crt, .pem, .cer" @file-changed="uploadOpMonitorCaFile">
            <XrdBtn
              data-test="upload-op-monitor-ca-button"
              variant="outlined"
              prepend-icon="upload"
              :text="opMonitorCa ? 'opMonitorTrust.opMonitorCa.replace' : 'opMonitorTrust.opMonitorCa.upload'"
              :loading="uploading"
              @click="upload"
            />
          </XrdFileUpload>
        </template>
        <p class="body-regular px-4 pb-4" data-test="op-monitor-ca-description">{{ $t('opMonitorTrust.opMonitorCa.description') }}</p>
        <p class="body-regular px-4 pb-4" data-test="op-monitor-ca-restart-hint">{{ $t('opMonitorTrust.opMonitorCa.restartHint') }}</p>
        <p v-if="!opMonitorCa && !loadingOpMonitorCa" class="body-regular px-4 pb-4 opacity-60" data-test="op-monitor-ca-empty">
          {{ $t('opMonitorTrust.opMonitorCa.empty') }}
        </p>
        <CaCertificateDetails v-if="opMonitorCa" :certificate="opMonitorCa" test-id-prefix="op-monitor-ca" />
      </XrdCard>
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { FileUploadResult, XrdBtn, XrdCard, XrdFileUpload, XrdSubView, XrdView, useNotifications } from '@niis/shared-ui';
import { CertificateDetails } from '@/openapi-types';
import { Permissions } from '@/global';
import { useOpMonitorTrust } from '@/store/modules/op-monitor-trust';
import { useUser } from '@/store/modules/user';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';
import CaCertificateDetails from '@/views/KeysAndCertificates/OpMonitorTrust/CaCertificateDetails.vue';

const { hasPermission } = useUser();
const { fetchInternalRpcCa, downloadInternalRpcCa, fetchOpMonitorCa, uploadOpMonitorCa } = useOpMonitorTrust();
const { addError, addSuccessMessage } = useNotifications();

const internalRpcCa = ref<CertificateDetails | undefined>(undefined);
const loading = ref(false);
const unavailable = ref(false);
const downloading = ref(false);
const opMonitorCa = ref<CertificateDetails | undefined>(undefined);
const loadingOpMonitorCa = ref(false);
const uploading = ref(false);

const canDownload = computed(() => hasPermission(Permissions.DOWNLOAD_INTERNAL_RPC_CA));
const canViewOpMonitorCa = computed(() => hasPermission(Permissions.VIEW_OP_MONITOR_CA));
const canUploadOpMonitorCa = computed(() => hasPermission(Permissions.UPLOAD_OP_MONITOR_CA));

function fetchData(): void {
  loading.value = true;
  fetchInternalRpcCa()
    .then((details) => (internalRpcCa.value = details))
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
  downloadInternalRpcCa()
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

fetchData();
if (canViewOpMonitorCa.value) {
  fetchOpMonitorCaData();
}
</script>

<style lang="scss" scoped></style>
