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
  <XrdView data-test="issuer-service-view" title="tab.main.settings" :loading="loading">
    <template #tabs>
      <SettingsViewTabs />
    </template>
    <XrdSubView>
      <XrdCard data-test="issuer-service-card" title="tab.settings.issuerService">
        <template v-if="canInitialize" #title-actions>
          <XrdBtn
            data-test="issuer-service-initialize-button"
            variant="outlined"
            text="issuerService.initialize"
            :loading="initializing"
            :disabled="!initializeEnabled || initializing"
            @click="showInitializeConfirm = true"
          />
        </template>
        <v-alert v-if="loadFailed" data-test="issuer-service-retry-alert" type="error" variant="tonal" class="mb-4 mx-4">
          {{ $t('issuerService.loadFailed') }}
          <XrdBtn data-test="issuer-service-retry-button" variant="text" text="issuerService.retry" @click="loadStatus" />
        </v-alert>
        <template v-else-if="state">
          <v-alert v-if="state.run_in_progress" data-test="issuer-service-run-in-progress" type="info" variant="tonal" class="mb-4 mx-4">
            {{ $t('issuerService.runInProgress') }}
          </v-alert>
          <XrdCardTable>
            <XrdCardTableRow data-test="issuer-service-host-row" label="issuerService.host">
              <template #value>
                <XrdStatusChip
                  :type="state.host_set ? 'success' : 'error'"
                  :text="state.host_set ? '' : 'issuerService.hostNotSet'"
                  :translated-text="state.host ?? ''"
                />
              </template>
              <XrdBtn
                v-if="canEditHost"
                data-test="issuer-service-host-edit-button"
                variant="text"
                color="tertiary"
                text="action.edit"
                @click="showEditHostDialog = true"
              />
            </XrdCardTableRow>
            <XrdCardTableRow data-test="issuer-service-certificate-row" label="issuerService.certificate">
              <template #value>
                <XrdStatusChip
                  :type="state.certificate_ready ? 'success' : 'error'"
                  :text="state.certificate_ready ? 'issuerService.certificateReady' : 'issuerService.certificateNotReady'"
                />
              </template>
              <XrdBtn
                v-if="hasPermissionToViewDsTlsCertificate"
                data-test="issuer-service-certificate-link"
                variant="text"
                color="tertiary"
                text="issuerService.viewCertificate"
                @click="router.push({ name: RouteName.DsTlsCertificate })"
              />
            </XrdCardTableRow>
            <XrdCardTableRow data-test="issuer-service-can-sign-row" label="issuerService.service">
              <template #value>
                <XrdStatusChip :type="canSignStatus.type" :text="canSignStatus.text" />
              </template>
            </XrdCardTableRow>
            <XrdCardTableRow data-test="issuer-service-identity-row" label="issuerService.identity">
              <template #value>
                <XrdStatusChip
                  :type="state.bound ? 'success' : 'error'"
                  :text="identityDid ? '' : 'issuerService.identityNotSet'"
                  :translated-text="identityDid ?? ''"
                />
              </template>
            </XrdCardTableRow>
          </XrdCardTable>
        </template>
      </XrdCard>
      <TrustedIssuersCard v-if="canViewTrustedIssuers" class="mt-4" @removed="refreshStatus" />
    </XrdSubView>
    <EditIssuerServiceHostDialog
      v-if="showEditHostDialog"
      :host="state?.host ?? ''"
      @cancel="showEditHostDialog = false"
      @save="onHostSaved"
    />
    <XrdConfirmDialog
      v-if="showInitializeConfirm"
      data-test="issuer-service-initialize-confirm-dialog"
      title="issuerService.initializeConfirmTitle"
      text="issuerService.initializeConfirmText"
      :data="{ did: identityDid ?? '' }"
      accept-button-text="issuerService.initialize"
      @cancel="showInitializeConfirm = false"
      @accept="confirmInitialize"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  useNotifications,
  XrdBtn,
  XrdCard,
  XrdCardTable,
  XrdCardTableRow,
  XrdConfirmDialog,
  XrdStatusChip,
  XrdSubView,
  XrdView,
} from '@niis/shared-ui';
import SettingsViewTabs from '@/views/Settings/SettingsViewTabs.vue';
import EditIssuerServiceHostDialog from '@/views/Settings/IssuerService/EditIssuerServiceHostDialog.vue';
import TrustedIssuersCard from '@/views/Settings/IssuerService/TrustedIssuersCard.vue';
import { Permissions, RouteName } from '@/global';
import { useUser } from '@/store/modules/user';
import { useIssuerService } from '@/store/modules/issuer-service';
import { useAlerts } from '@/store/modules/alerts';
import { IssuerServiceState } from '@/openapi-types';
import { CAN_SIGN_CAUSE_TEXT } from '@/util/can-sign-cause-text';
import { usePolling } from '@/composables/use-polling';

const POLL_INTERVAL_MS = 5_000;

const router = useRouter();
const { hasPermission } = useUser();
const { getStatus, initialize } = useIssuerService();
const { checkAlerts } = useAlerts();
const { addError, addSuccessMessage } = useNotifications();

const state = ref<IssuerServiceState>();
const loading = ref(true);
const loadFailed = ref(false);
const showEditHostDialog = ref(false);
const showInitializeConfirm = ref(false);
const initializing = ref(false);

const polling = usePolling<IssuerServiceState>({
  intervalMs: POLL_INTERVAL_MS,
  request: getStatus,
  shouldContinue: (result) => result.run_in_progress,
  onResult: (result) => {
    const wasRunning = state.value?.run_in_progress;
    state.value = result;
    loadFailed.value = false;
    if (wasRunning && !result.run_in_progress) {
      checkAlerts();
    }
  },
  onError: (error) => {
    addError(error);
    if (!state.value) {
      loadFailed.value = true;
    }
  },
});

const hasPermissionToViewDsTlsCertificate = computed(() => hasPermission(Permissions.VIEW_DS_TLS_CERT));
const canEditHost = computed(() => hasPermission(Permissions.EDIT_ISSUER_SERVICE_HOST) && !state.value?.bound);
const canInitialize = computed(() => hasPermission(Permissions.INITIALIZE_ISSUER_SERVICE));
const canViewTrustedIssuers = computed(() => hasPermission(Permissions.VIEW_TRUSTED_ISSUERS));
const initializeEnabled = computed(
  () => !state.value?.bound && !!state.value?.host_set && !!state.value?.certificate_ready && !state.value?.run_in_progress,
);

const identityDid = computed(() => state.value?.bound_did ?? state.value?.did_to_bind);

const canSignStatus = computed(() => {
  if (state.value?.status === 'REMOVED') {
    return { type: 'error' as const, text: 'issuerService.serviceRemoved' };
  }
  if (state.value?.can_sign) {
    return { type: 'success' as const, text: 'issuerService.serviceCanSign' };
  }
  const cause = state.value?.can_sign_cause;
  return { type: 'error' as const, text: (cause && CAN_SIGN_CAUSE_TEXT[cause]) || 'issuerService.serviceCannotSign' };
});

function loadStatus() {
  loading.value = true;
  loadFailed.value = false;
  return polling.refresh().finally(() => (loading.value = false));
}

function refreshStatus() {
  return polling.refresh();
}

function onHostSaved(result?: IssuerServiceState) {
  showEditHostDialog.value = false;
  if (result) {
    state.value = result;
    polling.syncSchedule(result);
    checkAlerts();
  } else {
    loadStatus();
  }
}

function confirmInitialize() {
  showInitializeConfirm.value = false;
  initializing.value = true;
  initialize()
    .then(() => {
      addSuccessMessage('issuerService.initializeStarted');
      return polling.refresh();
    })
    .catch((error) => {
      addError(error);
      return polling.refresh();
    })
    .finally(() => (initializing.value = false));
}

onMounted(loadStatus);
</script>
