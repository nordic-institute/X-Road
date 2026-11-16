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
        <XrdCardTable>
          <XrdCardTableRow data-test="issuer-service-host-row" label="issuerService.host">
            <template #value>
              <XrdStatusChip
                :type="state?.host_set ? 'success' : 'error'"
                :text="state?.host_set ? '' : 'issuerService.hostNotSet'"
                :translated-text="state?.host ?? ''"
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
                :type="state?.certificate_ready ? 'success' : 'error'"
                :text="state?.certificate_ready ? 'issuerService.certificateReady' : 'issuerService.certificateNotReady'"
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
              <XrdStatusChip :type="state?.can_sign ? 'success' : 'error'" :text="canSignText" />
            </template>
          </XrdCardTableRow>
          <XrdCardTableRow data-test="issuer-service-identity-row" label="issuerService.identity">
            <template #value>
              <XrdStatusChip
                :type="state?.bound ? 'success' : 'error'"
                :text="state?.did_to_bind ? '' : 'issuerService.identityNotSet'"
                :translated-text="state?.did_to_bind ?? ''"
              />
            </template>
          </XrdCardTableRow>
        </XrdCardTable>
      </XrdCard>
    </XrdSubView>
    <EditIssuerServiceHostDialog
      v-if="showEditHostDialog"
      :host="state?.host ?? ''"
      @cancel="showEditHostDialog = false"
      @save="onHostSaved"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useNotifications, XrdBtn, XrdCard, XrdCardTable, XrdCardTableRow, XrdStatusChip, XrdSubView, XrdView } from '@niis/shared-ui';
import SettingsViewTabs from '@/views/Settings/SettingsViewTabs.vue';
import EditIssuerServiceHostDialog from '@/views/Settings/IssuerService/EditIssuerServiceHostDialog.vue';
import { Permissions, RouteName } from '@/global';
import { useUser } from '@/store/modules/user';
import { useIssuerService } from '@/store/modules/issuer-service';
import { IssuerServiceState } from '@/openapi-types';
import { CAN_SIGN_CAUSE_TEXT } from '@/util/can-sign-cause-text';

const router = useRouter();
const { hasPermission } = useUser();
const { getStatus } = useIssuerService();
const { addError } = useNotifications();

const state = ref<IssuerServiceState>();
const loading = ref(true);
const showEditHostDialog = ref(false);

const hasPermissionToViewDsTlsCertificate = computed(() => hasPermission(Permissions.VIEW_DS_TLS_CERT));
const canEditHost = computed(() => hasPermission(Permissions.EDIT_ISSUER_SERVICE_HOST) && !state.value?.bound);

const canSignText = computed(() => {
  if (state.value?.can_sign) {
    return 'issuerService.serviceCanSign';
  }
  const cause = state.value?.can_sign_cause;
  return (cause && CAN_SIGN_CAUSE_TEXT[cause]) || 'issuerService.serviceCannotSign';
});

async function loadStatus() {
  try {
    state.value = await getStatus();
  } catch (error) {
    addError(error);
  } finally {
    loading.value = false;
  }
}

function onHostSaved() {
  showEditHostDialog.value = false;
  loadStatus();
}

onMounted(loadStatus);
</script>
