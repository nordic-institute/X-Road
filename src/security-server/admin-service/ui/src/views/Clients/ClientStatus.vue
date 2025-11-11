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
  <div>
    <v-chip
      v-if="statusStyle"
      class="xrd"
      density="compact"
      variant="flat"
      :class="[statusStyle.bgColor]"
    >
      <template #prepend>
        <XrdStatusIcon class="mr-1 ml-n1" :status="statusStyle.status" />
      </template>
      <template #default>
        <span class="font-weight-medium" :class="statusStyle.textColor">
          {{ statusStyle.text }}
        </span>
      </template>
    </v-chip>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { XrdStatusIcon, Status } from '@niis/shared-ui';

type StatusStyle = {
  bgColor: string;
  text: string;
  textColor: string;
  status: Status;
};

const props = defineProps({
  status: {
    type: String,
    default: '',
  },
});

const { t } = useI18n();

const statusStyle = computed<StatusStyle | undefined>(() => {
  if (!props.status) {
    return undefined;
  }
  switch (props.status.toLowerCase()) {
    case 'registered':
      return asSuccess('registered', 'ok');
    case 'registration_in_progress':
      return asInfo('registrationInProgress', 'progress-register');
    case 'enabling_in_progress':
      return asInfo('enablingInProgress', 'progress-register');
    case 'saved':
      return asWarning('saved', 'saved');
    case 'deletion_in_progress':
      return asError('deletionInProgress', 'progress-delete');
    case 'disabling_in_progress':
      return asError('disablingInProgress', 'progress-delete');
    case 'name_set':
      return asWarning('nameSet', 'name-set');
    case 'name_submitted':
      return asInfo('nameSubmitted', 'name-submitted');
    case 'disabled':
      return asError('disabled', 'error-disabled');
    case 'global_error':
      return asError('globalError', 'error');
    default:
      return asError('', 'error');
  }
});

function asSuccess(textKey: string, status: Status) {
  return createStatusStyle(
    textKey,
    'bg-success-container',
    'on-success-container',
    status,
  );
}

function asInfo(textKey: string, status: Status) {
  return createStatusStyle(
    textKey,
    'bg-info-container',
    'on-info-container',
    status,
  );
}

function asWarning(textKey: string, status: Status) {
  return createStatusStyle(
    textKey,
    'bg-warning-container',
    'on-warning-container',
    status,
  );
}

function asError(textKey: string, status: Status) {
  return createStatusStyle(
    textKey,
    'bg-error-container',
    'on-error-container',
    status,
  );
}

function createStatusStyle(
  textKey: string,
  bgColor: string,
  textColor: string,
  status: Status,
): StatusStyle {
  return {
    text: t('client.statusText.' + textKey),
    bgColor,
    textColor,
    status,
  };
}
</script>

<style lang="scss" scoped></style>
