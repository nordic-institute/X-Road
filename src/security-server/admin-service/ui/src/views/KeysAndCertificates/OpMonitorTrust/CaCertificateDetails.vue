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
  <v-table class="xrd bg-surface-container" :data-test="`${testIdPrefix}-details`">
    <tbody>
      <tr>
        <td class="on-surface font-weight-medium">{{ $t('cert.subjectDistinguishedName') }}</td>
        <td :data-test="`${testIdPrefix}-subject`">{{ certificate.subject_distinguished_name }}</td>
      </tr>
      <tr>
        <td class="on-surface font-weight-medium">{{ $t('cert.issuerDistinguishedName') }}</td>
        <td :data-test="`${testIdPrefix}-issuer`">{{ certificate.issuer_distinguished_name }}</td>
      </tr>
      <tr>
        <td class="on-surface font-weight-medium">{{ $t('cert.hashInfo') }}</td>
        <td :data-test="`${testIdPrefix}-fingerprint`"><XrdHashValue wrap-friendly :value="certificate.hash" /></td>
      </tr>
      <tr>
        <td class="on-surface font-weight-medium">{{ $t('cert.notBefore') }}</td>
        <td :data-test="`${testIdPrefix}-not-before`"><XrdDate :value="certificate.not_before" /></td>
      </tr>
      <tr>
        <td class="on-surface font-weight-medium">{{ $t('cert.notAfter') }}</td>
        <td :data-test="`${testIdPrefix}-not-after`"><XrdDate :value="certificate.not_after" /></td>
      </tr>
    </tbody>
  </v-table>
</template>

<script lang="ts" setup>
import { PropType } from 'vue';
import { XrdDate, XrdHashValue } from '@niis/shared-ui';
import { CertificateDetails } from '@/openapi-types';

defineProps({
  certificate: {
    type: Object as PropType<CertificateDetails>,
    required: true,
  },
  testIdPrefix: {
    type: String,
    required: true,
  },
});
</script>

<style lang="scss" scoped></style>
