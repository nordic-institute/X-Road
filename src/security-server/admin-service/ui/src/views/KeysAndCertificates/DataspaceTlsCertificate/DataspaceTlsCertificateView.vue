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
  <XrdTlsCertificateView
    title="tab.keys.dsTlsCertificate"
    :can-view-certificate="true"
    :can-upload="uploadCertificateVisible"
    :can-generate-csr="false"
    :can-download="false"
    :can-generate-key="false"
    key-certificate-upload
    key-column-header="tlsCertificates.dsTls.key"
    key-row-label="tlsCertificates.dsTls.keyText"
    :handler="handler"
    :cert-details-view-name="certDetailsView"
  >
    <template #append-header>
      <DataspaceTlsEnrollmentStatusChip />
    </template>
    <template #tabs>
      <KeysAndCertificatesTabs />
    </template>
  </XrdTlsCertificateView>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { Permissions, RouteName } from '@/global';
import { XrdTlsCertificateView, TlsCertificatesHandler, TlsCertificate } from '@niis/shared-ui';
import { useUser } from '@/store/modules/user';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';
import DataspaceTlsEnrollmentStatusChip from '@/views/KeysAndCertificates/DataspaceTlsCertificate/DataspaceTlsEnrollmentStatusChip.vue';
import { useDataspaceTlsCertificate } from '@/store/modules/dataspace-tls-certificate';

const { hasPermission } = useUser();
const { fetchTlsCertificate, uploadKeyAndCertificate } = useDataspaceTlsCertificate();

const certDetailsView = RouteName.DsTlsCertificateDetails;

const uploadCertificateVisible = computed(() => hasPermission(Permissions.UPLOAD_DS_TLS_CERT));

const handler = computed<TlsCertificatesHandler>(() => ({
  fetchTlsCertificate(): Promise<TlsCertificate> {
    return fetchTlsCertificate();
  },
  downloadCertificate(): Promise<unknown> {
    return Promise.resolve();
  },
  generateCsr(): Promise<unknown> {
    return Promise.resolve();
  },
  generateKey(): Promise<unknown> {
    return Promise.resolve();
  },
  uploadCertificate(): Promise<unknown> {
    return Promise.resolve();
  },
  uploadKeyAndCertificate(keyFile: File, certificateFile: File): Promise<unknown> {
    return uploadKeyAndCertificate(keyFile, certificateFile);
  },
}));
</script>

<style lang="scss" scoped></style>
