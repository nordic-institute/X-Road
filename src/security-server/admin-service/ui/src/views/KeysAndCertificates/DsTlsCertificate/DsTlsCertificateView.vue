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
    :can-generate-csr="generateCsrVisible"
    :can-download="downloadCertificateVisible"
    :can-generate-key="generateKeyVisible"
    :handler="handler"
    :cert-details-view-name="certDetailsView"
  >
    <template #append-header>
      <v-chip v-if="keyGeneratedPending" color="warning" variant="outlined" class="ml-2">
        {{ $t('dsTlsCertificate.keyGeneratedPending') }}
      </v-chip>
    </template>
    <template #tabs>
      <KeysAndCertificatesTabs />
    </template>
    <template #append-card>
      <DsTlsCertificateEnrollmentStatus ref="enrollmentStatusRef" />
    </template>
  </XrdTlsCertificateView>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { Permissions, RouteName } from '@/global';
import { XrdTlsCertificateView, TlsCertificatesHandler, TlsCertificate, DsTlsCertificateStatus } from '@niis/shared-ui';
import { useUser } from '@/store/modules/user';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';
import DsTlsCertificateEnrollmentStatus from '@/views/KeysAndCertificates/DsTlsCertificate/DsTlsCertificateEnrollmentStatus.vue';
import { useDsTlsCertificate } from '@/store/modules/ds-tls-certificate';

const { hasPermission } = useUser();
const { fetchDsTlsCertificateStatus, downloadCertificate, uploadCertificate, generateCsr, generateKey } = useDsTlsCertificate();

const certDetailsView = RouteName.DsTlsCertificateDetails;

const uploadCertificateVisible = computed(() => hasPermission(Permissions.UPLOAD_DS_TLS_CERT));
const downloadCertificateVisible = computed(() => hasPermission(Permissions.DOWNLOAD_DS_TLS_CERT));
const generateKeyVisible = computed(() => hasPermission(Permissions.GENERATE_DS_TLS_KEY));
const generateCsrVisible = computed(() => hasPermission(Permissions.GENERATE_DS_TLS_CSR));

const status = ref<DsTlsCertificateStatus | null>(null);
const enrollmentStatusRef = ref<InstanceType<typeof DsTlsCertificateEnrollmentStatus> | null>(null);
const keyGeneratedPending = computed(() => status.value?.key_generated === true && !status.value?.certificate);

const handler = computed<TlsCertificatesHandler>(() => ({
  downloadCertificate(): Promise<unknown> {
    return downloadCertificate();
  },
  fetchTlsCertificate(): Promise<TlsCertificate> {
    return fetchDsTlsCertificateStatus().then((current) => {
      status.value = current;
      enrollmentStatusRef.value?.refresh();
      return current.certificate ?? { hash: '' };
    });
  },
  generateKey(): Promise<unknown> {
    return generateKey();
  },
  generateCsr(distinguishedName: string): Promise<unknown> {
    return generateCsr(distinguishedName);
  },
  uploadCertificate(file: File): Promise<unknown> {
    return uploadCertificate(file);
  },
}));
</script>

<style lang="scss" scoped></style>
