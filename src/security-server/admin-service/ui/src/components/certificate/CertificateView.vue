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
  <div class="certificate-details-wrapper xrd-default-shadow mx-auto">
    <xrd-sub-view-title :title="$t('cert.certificate')" @close="close" />
    <div class="pl-4">
      <div v-if="$slots.tools" class="detail-view-tools">
        <slot name="tools" />
      </div>

      <div class="detail-view-cert-hash">
        <CertificateHash :hash="certificateDetails.hash" />
      </div>
      <CertificateInfo :certificate="certificateDetails" />
    </div>
    <slot />
  </div>
</template>
<script lang="ts" setup>
import { PropType, useSlots } from 'vue';
import CertificateInfo from '@/components/certificate/CertificateInfo.vue';
import CertificateHash from '@/components/certificate/CertificateHash.vue';
import { useRouter } from 'vue-router';
import type { CertificateDetails } from '@/openapi-types';

defineProps({
  certificateDetails: {
    type: Object as PropType<CertificateDetails>,
    required: true,
  },
});

const slots = useSlots();

const router = useRouter();

function close() {
  router.back();
}
</script>
<style lang="scss" scoped>
@use '@/assets/detail-views';
@use '@niis/shared-ui/src/assets/wizards';
</style>
