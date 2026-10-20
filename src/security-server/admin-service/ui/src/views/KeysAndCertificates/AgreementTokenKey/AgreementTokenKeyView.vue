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
  <XrdView data-test="agreement-token-key-view" title="tab.keys.agreementTokenKey" :loading="loading">
    <template #tabs>
      <KeysAndCertificatesTabs />
    </template>
    <XrdSubView>
      <XrdCard title="agreementTokenKey.title">
        <template v-if="canRotate" #title-actions>
          <XrdBtn
            data-test="rotate-agreement-token-key-button"
            variant="outlined"
            prepend-icon="autorenew"
            text="agreementTokenKey.rotate"
            :loading="rotating"
            :disabled="rotating || !status?.available"
            @click="confirmRotate = true"
          />
        </template>
        <p class="body-regular px-4 pb-4" data-test="agreement-token-key-description">{{ $t('agreementTokenKey.description') }}</p>
        <p v-if="status && !status.available" class="body-regular px-4 pb-4 opacity-60" data-test="agreement-token-key-unavailable">
          {{ $t('agreementTokenKey.unavailable') }}
        </p>
        <v-table v-if="status?.available" class="xrd bg-surface-container">
          <tbody>
            <tr>
              <td>{{ $t('agreementTokenKey.keyId') }}</td>
              <td data-test="agreement-token-key-id">{{ status.active_key_id ?? '-' }}</td>
            </tr>
            <tr>
              <td>{{ $t('agreementTokenKey.createdAt') }}</td>
              <td data-test="agreement-token-key-created-at">
                <XrdDateTime v-if="status.active_key_created_at" :value="status.active_key_created_at" with-seconds />
                <template v-else>{{ $t('agreementTokenKey.createdAtUnknown') }}</template>
              </td>
            </tr>
            <tr>
              <td>{{ $t('agreementTokenKey.previousKeys') }}</td>
              <td data-test="agreement-token-key-previous-keys">{{ previousKeyCount }}</td>
            </tr>
          </tbody>
        </v-table>
        <p v-if="status?.pending_key_id" class="body-regular px-4 py-4" data-test="agreement-token-key-pending">
          {{ $t('agreementTokenKey.pending', { keyId: status.pending_key_id }) }}
        </p>
      </XrdCard>
      <XrdConfirmDialog
        v-if="confirmRotate"
        data-test="rotate-agreement-token-key-confirm-dialog"
        title="agreementTokenKey.rotateConfirmTitle"
        text="agreementTokenKey.rotateConfirmText"
        width="500"
        @cancel="confirmRotate = false"
        @accept="rotateConfirmed"
      />
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, ref } from 'vue';
import { XrdBtn, XrdCard, XrdConfirmDialog, XrdDateTime, XrdSubView, XrdView, useNotifications } from '@niis/shared-ui';
import { AgreementTokenKeyStatus } from '@/openapi-types';
import { Permissions } from '@/global';
import { useAgreementTokenKey } from '@/store/modules/agreement-token-key';
import { useUser } from '@/store/modules/user';
import KeysAndCertificatesTabs from '@/views/KeysAndCertificates/KeysAndCertificatesTabs.vue';

const { hasPermission } = useUser();
const { fetchStatus, rotate } = useAgreementTokenKey();
const { addError, addSuccessMessage } = useNotifications();

const status = ref<AgreementTokenKeyStatus | undefined>(undefined);
const loading = ref(false);
const rotating = ref(false);
const confirmRotate = ref(false);

const PENDING_POLL_INTERVAL_MS = 10_000;
let pollTimer: ReturnType<typeof setInterval> | undefined;
let readInFlight: Promise<void> | undefined;
let unmounted = false;

const canRotate = computed(() => hasPermission(Permissions.ROTATE_AGREEMENT_TOKEN_KEY));

const previousKeyCount = computed(() => {
  const current = status.value;
  if (!current) {
    return 0;
  }
  const notPrevious = (current.active_key_id ? 1 : 0) + (current.pending_key_id ? 1 : 0);
  return Math.max(current.retained_key_count - notPrevious, 0);
});

function stopPolling(): void {
  clearInterval(pollTimer);
  pollTimer = undefined;
}

function updatePolling(): void {
  if (status.value?.pending_key_id && !unmounted) {
    pollTimer ??= setInterval(pollStatus, PENDING_POLL_INTERVAL_MS);
  } else {
    stopPolling();
  }
}

function pollStatus(): void {
  if (!readInFlight) {
    loadStatus();
  }
}

function loadStatus(): Promise<void> {
  const read = (readInFlight ?? Promise.resolve()).then(readStatus);
  const tracked = read.finally(() => {
    if (readInFlight === tracked) {
      readInFlight = undefined;
    }
  });
  readInFlight = tracked;
  return tracked;
}

function readStatus(): Promise<void> {
  return fetchStatus()
    .then((result) => {
      status.value = result;
      updatePolling();
    })
    .catch((error) => {
      stopPolling();
      addError(error);
    });
}

function fetchData(): Promise<void> {
  loading.value = true;
  return loadStatus().finally(() => (loading.value = false));
}

function rotateConfirmed(): void {
  confirmRotate.value = false;
  rotating.value = true;
  rotate()
    .then(() => addSuccessMessage('agreementTokenKey.rotated'))
    .then(fetchData)
    .catch((error) => addError(error))
    .finally(() => (rotating.value = false));
}

onBeforeUnmount(() => {
  unmounted = true;
  stopPolling();
});

fetchData();
</script>

<style lang="scss" scoped></style>
