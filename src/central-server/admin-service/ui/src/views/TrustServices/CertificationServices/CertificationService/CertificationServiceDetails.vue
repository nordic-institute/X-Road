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
<!--
  Certification Service details view
-->
<template>
  <XrdSubView id="certification-service-details">
    <XrdCard :loading>
      <XrdCardTable>
        <XrdCardTableRow
          data-test="subject-distinguished-name-card"
          label="trustServices.trustService.details.subjectDistinguishedName"
          :value="currentCertificationService?.subject_distinguished_name || ''"
        />
        <XrdCardTableRow
          data-test="issuer-distinguished-name-card"
          label="trustServices.trustService.details.issuerDistinguishedName"
          :value="currentCertificationService?.issuer_distinguished_name || ''"
        />
        <XrdCardTableRow data-test="valid-from-card" label="trustServices.validFrom">
          <template #value>
            <XrdDateTime :value="currentCertificationService?.not_before" />
          </template>
        </XrdCardTableRow>
        <XrdCardTableRow data-test="valid-to-card" label="trustServices.validTo">
          <template #value>
            <XrdDateTime :value="currentCertificationService?.not_after" />
          </template>
        </XrdCardTableRow>
      </XrdCardTable>
    </XrdCard>
  </XrdSubView>
</template>

<script lang="ts" setup>
import { computed } from 'vue';

import { XrdCard, XrdCardTable, XrdCardTableRow, XrdDateTime, XrdSubView } from '@niis/shared-ui';

import { useCertificationService } from '@/store/modules/trust-services';

const certificationServiceStore = useCertificationService();

const currentCertificationService = computed(() => certificationServiceStore.current);
const loading = computed(() => certificationServiceStore.loadingCurrent);
</script>

<style lang="scss" scoped></style>
