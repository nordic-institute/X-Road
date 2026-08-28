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
  <XrdCard
    v-if="status || loading"
    class="mt-4"
    title="dsTlsCertificate.enrollmentStatus.title"
    data-test="ds-tls-enrollment-status"
    :loading="loading"
  >
    <XrdCardTable v-if="status">
      <XrdCardTableRow label="dsTlsCertificate.enrollmentStatus.method.label">
        <template #value>
          <XrdStatusChip :type="methodChip.type" :translated-text="$t(methodChip.textKey)" />
        </template>
      </XrdCardTableRow>
      <XrdCardTableRow v-if="status.next_renewal_time" label="dsTlsCertificate.enrollmentStatus.nextRenewalTime">
        <template #value>
          <span data-test="ds-tls-enrollment-next-renewal">
            {{ formatDate(status.next_renewal_time) }}
            <v-tooltip activator="parent" location="top">{{ formatDateTime(status.next_renewal_time) }}</v-tooltip>
          </span>
        </template>
      </XrdCardTableRow>
      <XrdCardTableRow v-if="status.last_error" label="dsTlsCertificate.enrollmentStatus.lastError">
        <template #value>
          <XrdStatusChip type="error" :translated-text="status.last_error" />
        </template>
      </XrdCardTableRow>
    </XrdCardTable>
  </XrdCard>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { XrdCard, XrdCardTable, XrdCardTableRow, XrdStatusChip, formatDate, formatDateTime, useNotifications } from '@niis/shared-ui';
import { useDsTlsCertificate } from '@/store/modules/ds-tls-certificate';
import { DataspaceTlsCertificateEnrollmentStatus } from '@/openapi-types';

const { fetchDsTlsCertificateEnrollmentStatus } = useDsTlsCertificate();
const { addError } = useNotifications();

const status = ref<DataspaceTlsCertificateEnrollmentStatus | null>(null);
const loading = ref(false);

async function refresh() {
  loading.value = true;
  try {
    status.value = await fetchDsTlsCertificateEnrollmentStatus();
  } catch (error) {
    status.value = null;
    addError(error);
  } finally {
    loading.value = false;
  }
}

onMounted(refresh);

defineExpose({ refresh });

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
</script>

<style lang="scss" scoped></style>
