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
    id="timestamping-service-certificate-details"
    :breadcrumbs
    close-on-escape
  >
    <XrdCertificate
      v-if="certificateDetails"
      :certificate="certificateDetails"
    />
  </XrdElevatedView>
</template>

<script lang="ts" setup>
import { computed, onMounted } from 'vue';
import { useTimestampingServices } from '@/store/modules/trust-services';
import { XrdCertificate, XrdElevatedView } from '@niis/shared-ui';
import { RouteName } from '@/global';
import { useI18n } from 'vue-i18n';

const props = defineProps({
  timestampingServiceId: {
    type: String,
    required: true,
  },
});

const { t } = useI18n();
const timestampingServicesStore = useTimestampingServices();

const timestampingService = computed(() => {
  const tsaId = Number(props.timestampingServiceId);
  return timestampingServicesStore.timestampingServices.find(
    (tsa) => tsa.id === tsaId,
  );
});

const certificateDetails = computed(() => {
  return timestampingService.value?.certificate;
});

const breadcrumbs = computed(() => [
  {
    title: t('tab.main.trustServices'),
    to: { name: RouteName.TrustServices },
  },
  {
    title: t('trustServices.timestampingServices'),
  },
  {
    title: t('cert.certificate'),
  },
]);

onMounted(() => window.scrollTo(0, 0));
</script>
