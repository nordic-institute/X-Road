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
  <XrdDsTlsCertificateView
    title="tab.keys.dsTlsCertificate"
    :can-view-certificate="true"
    :can-upload="uploadCertificateVisible"
    :can-generate-csr="generateCsrVisible"
    :can-download="downloadCertificateVisible"
    :can-generate-key="generateKeyVisible"
    :can-order="orderCertificateVisible"
    :handler="handler"
    :cert-details-view-name="certDetailsView"
  >
    <template #tabs>
      <KeysAndCertificatesTabs />
    </template>
  </XrdDsTlsCertificateView>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { Permissions, RouteName } from '@/global';
import { XrdDsTlsCertificateView, DsTlsCertificateHandler } from '@niis/shared-ui';
import { useUser } from '@/store/modules/user';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';
import { useDsTlsCertificate } from '@/store/modules/ds-tls-certificate';

const { hasPermission } = useUser();
const {
  fetchDsTlsCertificateStatus,
  fetchDsTlsCertificateEnrollmentStatus,
  downloadCertificate,
  uploadCertificate,
  generateCsr,
  generateKey,
  orderCertificate,
} = useDsTlsCertificate();

const certDetailsView = RouteName.DsTlsCertificateDetails;

const uploadCertificateVisible = computed(() => hasPermission(Permissions.UPLOAD_DS_TLS_CERT));
const downloadCertificateVisible = computed(() => hasPermission(Permissions.DOWNLOAD_DS_TLS_CERT));
const generateKeyVisible = computed(() => hasPermission(Permissions.GENERATE_DS_TLS_KEY));
const generateCsrVisible = computed(() => hasPermission(Permissions.GENERATE_DS_TLS_CSR));
const orderCertificateVisible = computed(() => hasPermission(Permissions.ORDER_DS_TLS_CERT));

const handler = computed<DsTlsCertificateHandler>(() => ({
  fetchStatus() {
    return fetchDsTlsCertificateStatus();
  },
  fetchTlsCertificate() {
    return fetchDsTlsCertificateStatus().then((current) => current.certificate ?? { hash: '' });
  },
  fetchEnrollmentStatus() {
    return fetchDsTlsCertificateEnrollmentStatus();
  },
  downloadCertificate() {
    return downloadCertificate();
  },
  generateKey() {
    return generateKey();
  },
  generateCsr(distinguishedName: string, subjectAltName?: string) {
    return generateCsr(distinguishedName, subjectAltName);
  },
  uploadCertificate(file: File) {
    return uploadCertificate(file);
  },
  orderCertificate(caName: string, distinguishedName: string, subjectAltName: string) {
    return orderCertificate(caName, distinguishedName, subjectAltName);
  },
}));
</script>

<style lang="scss" scoped></style>
