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
  <div v-if="status" class="ml-4" data-test="dstls-enrollment-status">
    <XrdStatusChip :type="chip.type">
      <template #text>
        <span class="body-small">
          <span class="font-weight-medium">{{ $t(chip.text) }}</span>
          <span v-if="chip.additionalText">
            &nbsp;{{ chip.additionalText }}
            <v-tooltip v-if="chip.tooltipText" activator="parent" location="top">
              {{ chip.tooltipText }}
            </v-tooltip>
          </span>
        </span>
      </template>
    </XrdStatusChip>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { XrdStatusChip, formatDate, formatDateTime, useNotifications } from '@niis/shared-ui';
import { DataspaceTlsCertificateEnrollmentStatus } from '@/openapi-types';
import { useDataspaceTlsCertificate } from '@/store/modules/dataspace-tls-certificate';

type Chip = {
  type: 'error' | 'success' | 'info' | 'inactive';
  text: string;
  additionalText?: string;
  tooltipText?: string;
};

const { t } = useI18n();
const { addError } = useNotifications();
const { fetchEnrollmentStatus } = useDataspaceTlsCertificate();

const status = ref<DataspaceTlsCertificateEnrollmentStatus | undefined>(undefined);

const chip = computed<Chip>(() => {
  const value = status.value;
  if (!value) {
    return { type: 'inactive', text: 'dsTlsCertificate.enrollmentStatus.none' };
  }
  if (value.last_error) {
    return {
      type: 'error',
      text: 'dsTlsCertificate.enrollmentStatus.error',
      additionalText: value.last_error,
    };
  }
  if (value.enrollment_method === 'ACME') {
    return value.next_renewal_time
      ? {
          type: 'success',
          text: 'dsTlsCertificate.enrollmentStatus.acme',
          additionalText: t('dsTlsCertificate.enrollmentStatus.nextRenewal', [formatDate(value.next_renewal_time)]),
          tooltipText: formatDateTime(value.next_renewal_time),
        }
      : { type: 'success', text: 'dsTlsCertificate.enrollmentStatus.acme' };
  }
  if (value.enrollment_method === 'MANUAL') {
    return { type: 'info', text: 'dsTlsCertificate.enrollmentStatus.manual' };
  }
  return { type: 'inactive', text: 'dsTlsCertificate.enrollmentStatus.none' };
});

onMounted(() => {
  fetchEnrollmentStatus()
    .then((data) => (status.value = data))
    .catch((error) => addError(error));
});
</script>

<style lang="scss" scoped></style>
