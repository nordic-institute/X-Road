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
  <div v-if="status" class="ds-tls-enrollment-status" data-test="ds-tls-enrollment-status">
    <XrdStatusChip :type="methodChip.type" data-test="ds-tls-enrollment-method">
      <template #text>
        <span class="body-small">
          <span class="font-weight-medium">{{ $t(methodChip.textKey) }}</span>
          <span v-if="status.next_renewal_time" data-test="ds-tls-enrollment-next-renewal">
            &nbsp;{{ $t(renewalLabelKey) }} {{ formatDate(status.next_renewal_time) }}
            <v-tooltip activator="parent" location="top">{{ formatDateTime(status.next_renewal_time) }}</v-tooltip>
          </span>
        </span>
      </template>
    </XrdStatusChip>
    <XrdStatusChip v-if="status.last_error" type="error" data-test="ds-tls-enrollment-error">
      <template #text>
        <span class="ds-tls-enrollment-error-text">
          {{ status.last_error }}
          <v-tooltip activator="parent" location="top">{{ status.last_error }}</v-tooltip>
        </span>
      </template>
    </XrdStatusChip>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { XrdStatusChip, formatDate, formatDateTime, useNotifications } from '@niis/shared-ui';
import { useDsTlsCertificate } from '@/store/modules/ds-tls-certificate';
import { DataspaceTlsCertificateEnrollmentStatus } from '@/openapi-types';

const { fetchDsTlsCertificateEnrollmentStatus } = useDsTlsCertificate();
const { addError } = useNotifications();

const status = ref<DataspaceTlsCertificateEnrollmentStatus | null>(null);

async function fetchStatus() {
  try {
    status.value = await fetchDsTlsCertificateEnrollmentStatus();
  } catch (error) {
    status.value = null;
    addError(error);
  }
}

onMounted(() => {
  fetchStatus();
});

const methodChip = computed(() => {
  switch (status.value?.enrollment_method) {
    case 'ACME':
      return { type: 'success' as const, textKey: 'dsTlsCertificate.enrollmentStatus.method.acme' };
    case 'MANUAL':
      return { type: 'info' as const, textKey: 'dsTlsCertificate.enrollmentStatus.method.manual' };
    default:
      return { type: 'inactive' as const, textKey: 'dsTlsCertificate.enrollmentStatus.method.none' };
  }
});

const renewalLabelKey = computed(() => {
  const nextRenewalTime = status.value?.next_renewal_time;
  const isFuture = !!nextRenewalTime && new Date(nextRenewalTime).getTime() > Date.now();
  return isFuture ? 'dsTlsCertificate.enrollmentStatus.nextRenewal' : 'dsTlsCertificate.enrollmentStatus.wasDue';
});
</script>

<style lang="scss" scoped>
.ds-tls-enrollment-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.ds-tls-enrollment-error-text {
  display: inline-block;
  max-width: 240px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  vertical-align: bottom;
}
</style>
