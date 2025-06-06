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
  <details-view :back-to="backTo">
    <XrdEmptyPlaceholder
      :loading="loading"
      :data="managementRequest"
      :no-items-text="$t('noData.noData')"
      skeleton-type="table-heading"
    />
    <xrd-titled-view v-if="managementRequest && !loading" :title="typeText">
      <template v-if="managementRequest.status === 'WAITING'" #header-buttons>
        <xrd-button
          outlined
          class="mr-4"
          data-test="approve-button"
          @click="showApproveDialog = true"
        >
          {{ $t('action.approve') }}
        </xrd-button>
        <xrd-button
          outlined
          class="mr-4"
          data-test="decline-button"
          @click="showDeclineDialog = true"
        >
          {{ $t('action.decline') }}
        </xrd-button>
      </template>
      <mr-information :management-request="managementRequest" />
      <div class="management-request-additional-details">
        <mr-security-server-information
          :class="{ 'only-half': onlyServerInfo }"
          :management-request="managementRequest"
        />
        <mr-client-information
          v-if="hasClientInfo"
          :management-request="managementRequest"
        />
        <mr-certificate-information
          v-if="hasCertificateInfo"
          :management-request="managementRequest"
        />
      </div>
      <mr-confirm-dialog
        v-if="
          showApproveDialog &&
          managementRequest.id &&
          managementRequest.security_server_id.encoded_id
        "
        :request-id="managementRequest.id"
        :security-server-id="managementRequest.security_server_id.encoded_id"
        :new-member="newMember"
        @approve="approve"
        @cancel="showApproveDialog = false"
      />
      <mr-decline-dialog
        v-if="
          showDeclineDialog &&
          managementRequest.id &&
          managementRequest.security_server_id.encoded_id
        "
        :request-id="managementRequest.id"
        :security-server-id="managementRequest.security_server_id.encoded_id"
        @decline="decline"
        @cancel="showDeclineDialog = false"
      />
    </xrd-titled-view>
  </details-view>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useManagementRequests } from '@/store/modules/management-requests';
import { managementTypeToText } from '@/util/helpers';
import { ManagementRequestType } from '@/openapi-types';
import MrDeclineDialog from '@/components/managementRequests/MrDeclineDialog.vue';
import MrConfirmDialog from '@/components/managementRequests/MrConfirmDialog.vue';
import MrCertificateInformation from '@/components/managementRequests/details/MrCertificateInformation.vue';
import MrClientInformation from '@/components/managementRequests/details/MrClientInformation.vue';
import MrSecurityServerInformation from '@/components/managementRequests/details/MrSecurityServerInformation.vue';
import MrInformation from '@/components/managementRequests/details/MrInformation.vue';
import { useNotifications } from '@/store/modules/notifications';
import DetailsView from '@/components/ui/DetailsView.vue';
import { RouteName } from '@/global';
import { XrdTitledView } from '@niis/shared-ui';

/**
 * Wrapper component for a certification service view
 */
const props = defineProps({
  requestId: {
    type: Number,
    required: true,
  },
});
const loading = ref(false);
const showApproveDialog = ref(false);
const showDeclineDialog = ref(false);
const backTo = {
  name: RouteName.ManagementRequests,
};

const managementRequests = useManagementRequests();
const { showError } = useNotifications();

const managementRequest = computed(
  () => managementRequests.currentManagementRequest,
);
const typeText = computed(() =>
  managementTypeToText(managementRequest.value?.type),
);
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
  loading.value = true;
  managementRequests
    .loadById(props.requestId)
    .catch((err) => showError(err))
    .finally(() => (loading.value = false));
}

fetchData();
</script>
<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/tables' as *;
@use  '@niis/shared-ui/src/assets/colors';

.management-request-additional-details {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-start;

  margin-bottom: 24px;

  /* eslint-disable-next-line vue-scoped-css/no-unused-selector */
  .details-block {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }
  }
}

.only-half {
  max-width: 50%;
}
</style>
