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
  <XrdElevatedViewFixedWidth
    title="cert.certificate"
    fixed-height
    :breadcrumbs="breadcrumbs"
    :loading="loading"
    @close="close"
  >
    <XrdCertificate v-if="certificate" :certificate="certificate" />
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue';
import { CertificateDetails } from '@/openapi-types';
import { XrdElevatedViewFixedWidth, XrdCertificate, useNotifications } from '@niis/shared-ui';
import { useTlsCertificate } from '@/store/modules/tls-certificate';
import { useRouter } from 'vue-router';
import { RouteName } from '@/global';
import { useI18n } from 'vue-i18n';

const { fetchTlsCertificate } = useTlsCertificate();

const router = useRouter();
const { t } = useI18n();
const { addError } = useNotifications();
const certificate = ref<CertificateDetails | undefined>(undefined);
const loading = ref(false);

const breadcrumbs = computed(() => [
  {
    title: t('tab.main.keys'),
    to: {
      name: RouteName.SignAndAuthKeys,
    },
  },
  {
    title: t('tab.keys.ssTlsCertificate'),
    to: {
      name: RouteName.SSTlsCertificate,
    },
  },
  {
    title: t('cert.certificate'),
  },
]);

function fetchData(): void {
  loading.value = true;
  fetchTlsCertificate()
    .then((cert) => (certificate.value = cert))
    .catch((error) => addError(error))
    .finally(() => (loading.value = false));
}

function close() {
  router.push({
    name: RouteName.SSTlsCertificate,
  });
}

fetchData();
</script>

<style lang="scss" scoped></style>
