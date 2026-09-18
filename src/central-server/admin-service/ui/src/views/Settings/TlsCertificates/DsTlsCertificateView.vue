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
    title="tab.settings.dsTlsCertificate"
    :cert-details-view-name="detailsViewName"
    :can-download="hasPermissionToDownloadCertificate"
    :can-generate-csr="hasPermissionToGenerateCsr"
    :can-generate-key="hasPermissionToGenerateKey"
    :can-upload="hasPermissionToUploadCertificate"
    :can-view-certificate="hasPermissionToViewCertificate"
    :can-order="hasPermissionToOrderCertificate"
    :handler="handler"
  >
    <template #tabs>
      <SettingsViewTabs />
    </template>
  </XrdDsTlsCertificateView>
</template>

<script lang="ts" setup>
import { XrdDsTlsCertificateView, DsTlsCertificateHandler } from '@niis/shared-ui';
import SettingsViewTabs from '../SettingsViewTabs.vue';
import { useUser } from '@/store/modules/user';
import { computed } from 'vue';
import { Permissions, RouteName } from '@/global';
import { useDsTlsCertificate } from '@/store/modules/ds-tls-certificate';

const { hasPermission } = useUser();
const {
  getStatus,
  uploadCertificate,
  generateCsr,
  generateKey,
  downloadCertificate,
  fetchDsTlsCertificateEnrollmentStatus,
  orderCertificate,
} = useDsTlsCertificate();

const detailsViewName = RouteName.DsTlsCertificateDetails;

const hasPermissionToDownloadCertificate = computed(() => hasPermission(Permissions.DOWNLOAD_DS_TLS_CERT));
const hasPermissionToViewCertificate = computed(() => hasPermission(Permissions.VIEW_DS_TLS_CERT));
const hasPermissionToGenerateKey = computed(() => hasPermission(Permissions.GENERATE_DS_TLS_KEY));
const hasPermissionToGenerateCsr = computed(() => hasPermission(Permissions.GENERATE_DS_TLS_CSR));
const hasPermissionToUploadCertificate = computed(() => hasPermission(Permissions.UPLOAD_DS_TLS_CERT));
const hasPermissionToOrderCertificate = computed(() => hasPermission(Permissions.ORDER_DS_TLS_CERT));

const handler = computed<DsTlsCertificateHandler>(() => ({
  fetchStatus() {
    return getStatus();
  },
  fetchTlsCertificate() {
    return getStatus().then((current) => current.certificate ?? { hash: '' });
  },
  fetchEnrollmentStatus() {
    return fetchDsTlsCertificateEnrollmentStatus();
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
  downloadCertificate() {
    return downloadCertificate();
  },
  orderCertificate(caName: string, distinguishedName: string, subjectAltName: string) {
    return orderCertificate(caName, distinguishedName, subjectAltName);
  },
}));
</script>
