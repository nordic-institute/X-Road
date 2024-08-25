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
  <div class="cert-row-wrap">
    <xrd-status-icon :status="status.icon" />
    <div class="status-text">{{ status.text }}</div>
    <div v-if="status.additionalText" v-tooltip="status.tooltipText">
      &nbsp;{{ status.additionalText }}status.tooltipText
      <v-tooltip v-if="status.tooltipText" activator="parent" location="top" 
        >{{ status.tooltipText }}</v-tooltip
      >
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { CertificateStatus, TokenCertificate } from '@/openapi-types';
import { formatDateTime } from '@/util/helpers';

export default defineComponent({
  props: {
    certificate: {
      type: Object as PropType<TokenCertificate>,
      required: true,
    },
    isAcmeCertificate: {
      type: Boolean,
    },
  },
  computed: {
    status() {
      if (
        !this.isAcmeCertificate ||
        this.certificate.status !== CertificateStatus.REGISTERED
      ) {
        return {
          icon: '',
          text: 'N/A',
        };
      }
      if (this.certificate.renewal_error) {
        return {
          icon: 'error',
          text: 'Renewal error:',
          additionalText: this.certificate.renewal_error,
        };
      }
      if (this.certificate.renewed_cert_hash) {
        return {
          icon: 'progress-register',
          text: 'Renewal in progress',
        };
      }
      const dateOnly = 'YYYY-MM-DD';
      const dateAndTime = 'YYYY-MM-DD HH:mm:ss';
      return {
        icon: 'ok',
        text: 'Next planned renewal on',
        additionalText: formatDateTime(
          this.certificate.next_automatic_renewal_time,
          dateOnly,
        ),
        tooltipText: formatDateTime(
          this.certificate.next_automatic_renewal_time,
          dateAndTime,
        ),
      };
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';

.cert-row-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.status-text {
  font-style: normal;
  font-weight: bold;
  font-size: 12px;
  line-height: 16px;
  color: $XRoad-WarmGrey100;
  margin-left: 2px;
  text-transform: uppercase;
  white-space: nowrap;
}
</style>
