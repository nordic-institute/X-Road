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
  <XrdView data-test="op-monitor-trust-view" title="tab.keys.opMonitorTrust" :loading="loading">
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
        <v-table v-if="internalRpcCa" class="xrd bg-surface-container" data-test="internal-rpc-ca-details">
          <tbody>
            <tr>
              <td class="on-surface font-weight-medium">{{ $t('cert.subjectDistinguishedName') }}</td>
              <td data-test="internal-rpc-ca-subject">{{ internalRpcCa.subject_distinguished_name }}</td>
            </tr>
            <tr>
              <td class="on-surface font-weight-medium">{{ $t('cert.issuerDistinguishedName') }}</td>
              <td data-test="internal-rpc-ca-issuer">{{ internalRpcCa.issuer_distinguished_name }}</td>
            </tr>
            <tr>
              <td class="on-surface font-weight-medium">{{ $t('cert.hashInfo') }}</td>
              <td data-test="internal-rpc-ca-fingerprint"><XrdHashValue wrap-friendly :value="internalRpcCa.hash" /></td>
            </tr>
            <tr>
              <td class="on-surface font-weight-medium">{{ $t('cert.notBefore') }}</td>
              <td data-test="internal-rpc-ca-not-before"><XrdDate :value="internalRpcCa.not_before" /></td>
            </tr>
            <tr>
              <td class="on-surface font-weight-medium">{{ $t('cert.notAfter') }}</td>
              <td data-test="internal-rpc-ca-not-after"><XrdDate :value="internalRpcCa.not_after" /></td>
            </tr>
          </tbody>
        </v-table>
      </XrdCard>
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { XrdBtn, XrdCard, XrdDate, XrdHashValue, XrdSubView, XrdView, useNotifications } from '@niis/shared-ui';
import { CertificateDetails } from '@/openapi-types';
import { Permissions } from '@/global';
import { useOpMonitorTrust } from '@/store/modules/op-monitor-trust';
import { useUser } from '@/store/modules/user';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';

const { hasPermission } = useUser();
const { fetchInternalRpcCa, downloadInternalRpcCa } = useOpMonitorTrust();
const { addError } = useNotifications();

const internalRpcCa = ref<CertificateDetails | undefined>(undefined);
const loading = ref(false);
const unavailable = ref(false);
const downloading = ref(false);

const canDownload = computed(() => hasPermission(Permissions.DOWNLOAD_INTERNAL_RPC_CA));

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

fetchData();
</script>

<style lang="scss" scoped></style>
