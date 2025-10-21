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
    title="tab.keys.signAndAuthKeys"
    :can-view-certificate="true"
    :can-upload="importCertificateVisible"
    :can-generate-csr="generateCsrVisible"
    :can-download="exportCertificateVisible"
    :can-generate-key="generateKeyVisible"
    :handler="handler"
    :cert-details-view-name="certDetailsView"
  >
    <template #append-header>
      <HelpButton
        class="ml-2"
        :help-image="helpImage"
        help-title="keys.helpTitleSS"
        help-text="keys.helpTextSS"
      />
    </template>
    <template #tabs>
      <KeysAndCertificatesTabs />
    </template>
  </XrdTlsCertificateView>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { Permissions, RouteName } from '@/global';
import {
  XrdTlsCertificateView,
  TlsCertificatesHandler,
  TlsCertificate,
} from '@niis/shared-ui';
import { useUser } from '@/store/modules/user';
import helpImg from '@/assets/tls_certificate.png';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';
import { useTlsCertificate } from '@/store/modules/tls-certificate';
import HelpButton from '@/components/ui/HelpButton.vue';

const { hasPermission } = useUser();
const {
  fetchTlsCertificate,
  downloadCertificate,
  uploadCertificate,
  generateCsr,
  generateKey,
} = useTlsCertificate();

const helpImage = ref(helpImg);
const certDetailsView = RouteName.InternalTlsCertificate;

const generateKeyVisible = computed(() =>
  hasPermission(Permissions.GENERATE_INTERNAL_TLS_KEY_CERT),
);
const importCertificateVisible = computed(() =>
  hasPermission(Permissions.IMPORT_INTERNAL_TLS_CERT),
);
const exportCertificateVisible = computed(() =>
  hasPermission(Permissions.EXPORT_INTERNAL_TLS_CERT),
);
const generateCsrVisible = computed(() =>
  hasPermission(Permissions.GENERATE_INTERNAL_TLS_CSR),
);
const handler = computed<TlsCertificatesHandler>(() => ({
  downloadCertificate(): Promise<unknown> {
    return downloadCertificate();
  },
  fetchTlsCertificate(): Promise<TlsCertificate> {
    return fetchTlsCertificate();
  },
  generateCsr(distinguishedName: string): Promise<unknown> {
    return generateCsr(distinguishedName);
  },
  generateKey(): Promise<unknown> {
    return generateKey();
  },
  uploadCertificate(file: File): Promise<unknown> {
    return uploadCertificate(file);
  },
}));
</script>

<style lang="scss" scoped></style>
