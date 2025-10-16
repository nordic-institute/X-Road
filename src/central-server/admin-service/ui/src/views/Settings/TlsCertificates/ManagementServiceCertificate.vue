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
    id="management-service-certificate-details"
    title="cert.certificate"
    close-on-escape
    go-back-on-close
    :loading
    :breadcrumbs="breadcrumbs"
  >
    <XrdCertificate
      v-if="certificateDetails"
      :certificate="certificateDetails"
    />
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue';

import {
  useRunning,
  XrdCertificate,
  XrdElevatedViewFixedWidth,
} from '@niis/shared-ui';

import { CertificateDetails as CertificateDetailsType } from '@/openapi-types';
import { useManagementServices } from '@/store/modules/management-services';
import { RouteName } from '@/global';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const { getCertificate } = useManagementServices();
const { loading, startLoading, stopLoading } = useRunning();
const certificateDetails = ref(null as CertificateDetailsType | null);

const breadcrumbs = computed(() => [
  {
    title: t('tab.main.settings'),
    to: {
      name: RouteName.Settings,
    },
  },
  {
    title: t('tab.settings.tlsCertificates'),
    to: {
      name: RouteName.TlsCertificates,
    },
  },
  {
    title: t('cert.certificate'),
  },
]);

function load() {
  startLoading();
  getCertificate()
    .then((resp) => (certificateDetails.value = resp.data))
    .finally(() => stopLoading());
}

load();
</script>
