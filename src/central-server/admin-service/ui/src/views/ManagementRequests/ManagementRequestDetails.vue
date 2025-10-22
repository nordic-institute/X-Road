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
  <XrdView translated-title :title="title" :breadcrumbs="breadcrumbs">
    <template v-if="managementRequest?.status === 'WAITING'" #append-header>
      <v-spacer />
      <XrdBtn
        data-test="decline-button"
        class="mr-4"
        variant="outlined"
        text="action.decline"
        color="primary"
        @click="showDeclineDialog = true"
      />

      <XrdBtn
        data-test="approve-button"
        class="mr-4"
        variant="flat"
        text="action.approve"
        color="primary"
        @click="showApproveDialog = true"
      />
    </template>

    <MrInformation :management-request="managementRequest" :loading />
    <v-container fluid class="management-request-additional-details pa-0 mt-6">
      <v-row justify="start">
        <v-col class="pa-2">
          <MrSecurityServerInformation
            class="fill-height"
            :management-request="managementRequest"
            :loading
          />
        </v-col>
        <v-col v-if="hasClientInfo" class="pa-2">
          <MrClientInformation
            class="fill-height"
            :management-request="managementRequest"
            :loading
          />
        </v-col>
        <v-col v-if="hasCertificateInfo" class="pa-2">
          <MrCertificateInformation
            class="fill-height"
            :management-request="managementRequest"
            :loading
          />
        </v-col>
        <v-spacer v-if="onlyServerInfo" />
      </v-row>
    </v-container>
    <MrConfirmDialog
      v-if="
        showApproveDialog &&
        managementRequest?.id &&
        managementRequest.security_server_id.encoded_id
      "
      :request-id="managementRequest.id"
      :security-server-id="managementRequest.security_server_id.encoded_id"
      :new-member="newMember"
      @approve="approve"
      @cancel="showApproveDialog = false"
    />
    <MrDeclineDialog
      v-if="
        showDeclineDialog &&
        managementRequest?.id &&
        managementRequest.security_server_id.encoded_id
      "
      :request-id="managementRequest.id"
      :security-server-id="managementRequest.security_server_id.encoded_id"
      @decline="decline"
      @cancel="showDeclineDialog = false"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, ref, watchEffect } from 'vue';
import { useManagementRequests } from '@/store/modules/management-requests';
import { ManagementRequestType } from '@/openapi-types';
import MrDeclineDialog from './dialogs/MrDeclineDialog.vue';
import MrConfirmDialog from './dialogs/MrConfirmDialog.vue';
import MrCertificateInformation from './MrCertificateInformation.vue';
import MrClientInformation from './MrClientInformation.vue';
import MrSecurityServerInformation from './MrSecurityServerInformation.vue';
import MrInformation from './MrInformation.vue';
import { XrdView, XrdBtn, useNotifications } from '@niis/shared-ui';
import { managementTypeToIconTextColor } from '@/util/helpers';
import { RouteName } from '@/global';

/**
 * Wrapper component for a certification service view
 */
const props = defineProps({
  requestId: {
    type: Number,
    required: true,
  },
});
const showApproveDialog = ref(false);
const showDeclineDialog = ref(false);
const managementRequests = useManagementRequests();

const { addError } = useNotifications();

const managementRequest = computed(() => managementRequests.current);
const loading = computed(() => managementRequests.loadingCurrent);

const title = computed(
  () =>
    managementTypeToIconTextColor(managementRequest.value?.type)?.text || '',
);
const breadcrumbs = computed(() => [
  {
    title: 'tab.main.managementRequests',
    to: {
      name: RouteName.ManagementRequests,
    },
  },
]);

const hasCertificateInfo = computed(() => {
  if (!managementRequest.value) {
    return false;
  }

  return [
    ManagementRequestType.AUTH_CERT_DELETION_REQUEST,
    ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST,
  ].includes(managementRequest.value.type);
});

const hasClientInfo = computed(() => {
  if (!managementRequest.value) {
    return false;
  }

  return [
    ManagementRequestType.CLIENT_DELETION_REQUEST,
    ManagementRequestType.CLIENT_REGISTRATION_REQUEST,
    ManagementRequestType.CLIENT_DISABLE_REQUEST,
    ManagementRequestType.CLIENT_ENABLE_REQUEST,
    ManagementRequestType.OWNER_CHANGE_REQUEST,
    ManagementRequestType.CLIENT_RENAME_REQUEST,
  ].includes(managementRequest.value.type);
});

const newClientOwner = computed(() => {
  const req = managementRequest.value;
  return (
    !!req &&
    req.type === ManagementRequestType.CLIENT_REGISTRATION_REQUEST &&
    !req.client_owner_name
  );
});

const newServerOwner = computed(() => {
  const req = managementRequest.value;
  return (
    !!req &&
    req.type === ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST &&
    !req.security_server_owner
  );
});

const newMember = computed(() => newClientOwner.value || newServerOwner.value);
const onlyServerInfo = computed(
  () => !hasCertificateInfo.value && !hasClientInfo.value,
);

function approve() {
  showApproveDialog.value = false;
  fetchData();
}

function decline() {
  showDeclineDialog.value = false;
  fetchData();
}

function fetchData() {
  managementRequests
    .loadById(props.requestId)
    .catch((err) => addError(err, { navigate: true }));
}

watchEffect(() => {
  fetchData();
});
</script>
