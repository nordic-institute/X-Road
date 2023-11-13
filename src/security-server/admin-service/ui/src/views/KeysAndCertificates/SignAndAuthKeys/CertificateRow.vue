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
  <tr>
    <td class="td-name">
      <div class="name-wrap" @click="certificateClick()">
        <i
          class="icon-Certificate cert-icon clickable-link"
          :style="{ color: certStatusColor }"
        />
        <div class="clickable-link" :style="{ color: certStatusColor }">
          {{ cert.certificate_details.issuer_common_name }}
          {{ cert.certificate_details.serial }}
        </div>
      </div>
    </td>
    <td>{{ cert.owner_id }}</td>
    <td>{{ $filters.ocspStatus(cert.ocsp_status) }}</td>
    <td>{{ $filters.formatDate(cert.certificate_details.not_after) }}</td>
    <td class="status-cell">
      <certificate-status-icon :certificate="cert" />
    </td>
    <td class="td-align-right">
      <slot name="certificateAction"></slot>
    </td>
  </tr>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import { defineComponent, PropType } from 'vue';
import CertificateStatusIcon from './CertificateStatusIcon.vue';
import { CertificateStatus } from '@/openapi-types';
import { TokenCertificate } from '@/openapi-types';
import { Colors } from '@/global';

export default defineComponent({
  components: {
    CertificateStatusIcon,
  },
  props: {
    cert: {
      type: Object as PropType<TokenCertificate>,
      required: true,
    },
  },
  emits: ['certificate-click'],
  computed: {
    certStatusColor(): string {
      return this.cert.status === CertificateStatus.GLOBAL_ERROR
        ? Colors.Error
        : '';
    },
  },

  methods: {
    certificateClick(): void {
      this.$emit('certificate-click');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.td-align-right {
  text-align: right;
}

.clickable-link {
  color: $XRoad-Purple100;
  cursor: pointer;
  height: 100%;
}

.cert-icon {
  margin-right: 18px;
  color: $XRoad-Purple100;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-left: 57px;
}
</style>
