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
  <div class="cert-row-wrap" data-test="renewal-status">
    <XrdStatusChip v-if="status.type" :type="status.type">
      <template #icon>
        <XrdStatusIcon class="mr-1 ml-n1" :status="status.icon" />
      </template>
      <template #text>
        <span class="body-small">
          <span class="font-weight-medium">{{ status.text }}</span>
          <span v-if="status.additionalText">
            &nbsp;{{ status.additionalText }}
            <v-tooltip
              v-if="status.tooltipText"
              activator="parent"
              location="top"
            >
              {{ status.tooltipText }}
            </v-tooltip>
          </span>
        </span>
      </template>
    </XrdStatusChip>
    <div v-else class="status-text">{{ status.text }}</div>
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';

import { helper, XrdStatusChip, XrdStatusIcon } from '@niis/shared-ui';

import { CertificateStatus, TokenCertificate } from '@/openapi-types';

type Status = {
  type?: 'error' | 'success' | 'info';
  icon: string;
  text: string;
  additionalText?: string;
  tooltipText?: string;
};

export default defineComponent({
  components: { XrdStatusChip, XrdStatusIcon },
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
    status(): Status {
      if (
        !this.isAcmeCertificate ||
        this.certificate.status !== CertificateStatus.REGISTERED
      ) {
        return {
          type: undefined,
          icon: '',
          text: 'N/A',
        };
      }
      if (this.certificate.renewal_error) {
        return {
          type: 'error',
          icon: 'error',
          text: 'Renewal error:',
          additionalText: this.certificate.renewal_error,
        };
      }
      if (this.certificate.renewed_cert_hash) {
        return {
          type: 'info',
          icon: 'progress-register',
          text: 'Renewal in progress',
        };
      }

      return {
        type: 'success',
        icon: 'ok',
        text: 'Next planned renewal on',
        additionalText: helper.formatDate(
          this.certificate.next_automatic_renewal_time,
        ),
        tooltipText: helper.formatDateTime(
          this.certificate.next_automatic_renewal_time,
        ),
      };
    },
  },
});
</script>

<style lang="scss" scoped></style>
