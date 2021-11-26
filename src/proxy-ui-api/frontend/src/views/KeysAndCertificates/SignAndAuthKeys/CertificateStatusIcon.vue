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
    <xrd-status-icon :status="statusIconType" />
    <div class="status-text">{{ $t(status) }}</div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { CertificateStatus, TokenCertificate } from '@/openapi-types';
import { Prop } from 'vue/types/options';

export default Vue.extend({
  props: {
    certificate: {
      type: Object as Prop<TokenCertificate>,
      required: true,
    },
  },
  data() {
    return {};
  },
  computed: {
    status() {
      switch (this.certificate.status) {
        case CertificateStatus.SAVED:
          return 'keys.certStatus.saved';

        case CertificateStatus.REGISTRATION_IN_PROGRESS:
          return 'keys.certStatus.registration';

        case CertificateStatus.REGISTERED:
          return 'keys.certStatus.registered';

        case CertificateStatus.DELETION_IN_PROGRESS:
          return 'keys.certStatus.deletion';

        case CertificateStatus.GLOBAL_ERROR:
          return 'keys.certStatus.globalError';

        default:
          if (!this.certificate.saved_to_configuration) {
            return 'keys.certStatus.onlyInHWToken';
          } else {
            return '-';
          }
      }
    },
    statusIconType() {
      switch (this.certificate.status) {
        case CertificateStatus.SAVED:
          return 'saved';

        case CertificateStatus.REGISTRATION_IN_PROGRESS:
          return 'progress-register';

        case CertificateStatus.REGISTERED:
          return 'ok';

        case CertificateStatus.DELETION_IN_PROGRESS:
          return 'progress-delete';

        case CertificateStatus.GLOBAL_ERROR:
          return 'error';
        default:
          return 'error';
      }
    },
  },
  methods: {},
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

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
