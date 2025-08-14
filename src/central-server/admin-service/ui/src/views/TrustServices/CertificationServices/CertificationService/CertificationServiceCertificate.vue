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
  <XrdElevatedView
    id="certification-service-certificate-details"
    close-on-escape
    :breadcrumbs
  >
    <XrdCertificate v-if="certificate" :certificate="certificate" />
  </XrdElevatedView>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue';
import { useCertificationService } from '@/store/modules/trust-services';
import { XrdCertificate, XrdElevatedView } from '@niis/shared-ui';
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

const { t } = useI18n();
const { getCertificate, currentCertificationService } =
  useCertificationService();

const breadcrumbs = computed(() => [
  {
    title: t('tab.main.trustServices'),
    to: { name: RouteName.TrustServices },
  },
  {
    title: currentCertificationService?.name || '',
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

getCertificate(Number(props.certificationServiceId)).then(
  (resp) => (certificate.value = resp.data),
);
</script>
