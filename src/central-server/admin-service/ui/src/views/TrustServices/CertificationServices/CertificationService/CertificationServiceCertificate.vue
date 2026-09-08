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
  <XrdElevatedViewFixedWidth id="certification-service-certificate-details" title="cert.certificate" go-back-on-close :breadcrumbs :loading>
    <XrdCertificate v-if="certificate" :certificate="certificate" />
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts" setup>
import { ref, computed, watchEffect } from 'vue';
import { useCertificationService } from '@/store/modules/trust-services';
import { XrdCertificate, XrdElevatedViewFixedWidth, useNotifications } from '@niis/shared-ui';
import { CertificateDetails } from '@/openapi-types';
import { RouteName } from '@/global';
import { useI18n } from 'vue-i18n';

type Certificate = CertificateDetails | null;

const props = defineProps({
  certificationServiceId: {
    type: String,
    required: true,
  },
});

const certificate = ref(null as Certificate);
const loading = ref(false);

const { t } = useI18n();
const { addError } = useNotifications();
const certificationServiceStore = useCertificationService();

const breadcrumbs = computed(() => [
  {
    title: t('tab.main.trustServices'),
    to: { name: RouteName.TrustServices },
  },
  {
    title: certificationServiceStore.current?.name || '',
    to: {
      name: RouteName.CertificationServiceDetails,
      params: {
        certificationServiceId: props.certificationServiceId,
      },
    },
  },
  {
    title: t('cert.certificate'),
  },
]);

watchEffect(() => {
  loading.value = true;
  const certId = Number(props.certificationServiceId);
  certificationServiceStore
    .loadById(certId)
    .then(() => certificationServiceStore.getCertificate(certId))
    .then((resp) => (certificate.value = resp.data))
    .catch((err) => addError(err, { navigate: true }))
    .finally(() => (loading.value = false));
});
</script>
