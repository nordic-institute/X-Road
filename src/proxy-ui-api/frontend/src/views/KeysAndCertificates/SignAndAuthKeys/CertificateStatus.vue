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
  <div class="row-wrap">
    <StatusIcon :status="statusIconType" />
    <div>{{ $t(status) }}</div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { CertificateStatus } from '@/global';
import StatusIcon from '@/components/ui/StatusIcon.vue';

export default Vue.extend({
  components: {
    StatusIcon,
  },
  props: {
    certificate: {
      type: Object,
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
          return '-';
      }
    },
    statusIconType() {
      switch (this.certificate.status) {
        case CertificateStatus.SAVED:
          return 'orange-ring';

        case CertificateStatus.REGISTRATION_IN_PROGRESS:
          return 'orange';

        case CertificateStatus.REGISTERED:
          return 'green';

        case CertificateStatus.DELETION_IN_PROGRESS:
          return 'red';

        case CertificateStatus.GLOBAL_ERROR:
          return 'red-ring';
        default:
          return 'red-ring';
      }
    },
  },
  methods: {},
});
</script>

<style lang="scss" scoped>
.row-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}
</style>
