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
  <div v-if="hasStatus" class="status-wrapper">
    <xrd-status-icon :status="statusIconType" />
    <div class="status-text">{{ getStatusText(status) }}</div>
  </div>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import { ManagementRequestStatus } from '@/openapi-types';

export default Vue.extend({
  props: {
    status: {
      type: String as PropType<ManagementRequestStatus>,
      default: undefined,
    },
  },

  computed: {
    hasStatus(): boolean {
      return this.status !== undefined;
    },
    statusIconType(): string {
      if (this.status) {
        switch (this.status) {
          case ManagementRequestStatus.REVOKED:
          case ManagementRequestStatus.DECLINED:
            return 'progress-delete';
          case ManagementRequestStatus.APPROVED:
            return 'ok';
          case ManagementRequestStatus.WAITING:
          case ManagementRequestStatus.SUBMITTED_FOR_APPROVAL:
            return 'pending';
        }
      }
      return 'error';
    },
  },

  methods: {
    getStatusText(status: ManagementRequestStatus): string {
      if (status) {
        switch (status) {
          case ManagementRequestStatus.WAITING:
            return this.$t('managementRequests.pending') as string;
          case ManagementRequestStatus.APPROVED:
            return this.$t('managementRequests.approved') as string;
          case ManagementRequestStatus.DECLINED:
            return this.$t('managementRequests.rejected') as string;
          case ManagementRequestStatus.REVOKED:
            return this.$t('managementRequests.revoked') as string;
          case ManagementRequestStatus.SUBMITTED_FOR_APPROVAL:
            return this.$t('managementRequests.submitted') as string;
        }
      }
      return this.$t('managementRequests.unknown') as string;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.status-text {
  font-style: normal;
  font-weight: bold;
  font-size: 12px;
  line-height: 16px;
  color: $XRoad-WarmGrey100;
  margin-left: 2px;
  text-transform: uppercase;
}
</style>
