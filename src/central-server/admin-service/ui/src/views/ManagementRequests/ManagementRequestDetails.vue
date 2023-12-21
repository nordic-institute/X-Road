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
    <titled-view v-if="managementRequest && !loading" :title="typeText">
      <template v-if="managementRequest.status === 'WAITING'" #header-buttons>
        <xrd-button
          outlined
          class="mr-4"
          data-test="approve-button"
          @click="$refs.approveDialog.openDialog()"
        >
          {{ $t('action.approve') }}
        </xrd-button>
        <xrd-button
          outlined
          class="mr-4"
          data-test="decline-button"
          @click="$refs.declineDialog.openDialog()"
        >
          {{ $t('action.decline') }}
        </xrd-button>
      </template>
      <mr-information :management-request="managementRequest" />
      <div class="management-request-additional-details">
        <mr-security-server-information
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
        ref="approveDialog"
        :request-id="managementRequest.id"
        :security-server-id="managementRequest.security_server_id.encoded_id"
        @approve="fetchData"
      />
      <mr-decline-dialog
        ref="declineDialog"
        :request-id="managementRequest.id"
        :security-server-id="managementRequest.security_server_id.encoded_id"
        @decline="fetchData"
      />
    </titled-view>
  </details-view>
</template>

<script lang="ts">
import Vue, { defineComponent } from 'vue';
import { mapActions, mapStores } from 'pinia';
import { useManagementRequests } from '@/store/modules/management-requests';
import { managementTypeToText } from '@/util/helpers';
import {
  ManagementRequestDetailedView,
  ManagementRequestType,
} from '@/openapi-types';
import MrDeclineDialog from '@/components/managementRequests/MrDeclineDialog.vue';
import MrConfirmDialog from '@/components/managementRequests/MrConfirmDialog.vue';
import MrCertificateInformation from '@/components/managementRequests/details/MrCertificateInformation.vue';
import MrClientInformation from '@/components/managementRequests/details/MrClientInformation.vue';
import MrSecurityServerInformation from '@/components/managementRequests/details/MrSecurityServerInformation.vue';
import MrInformation from '@/components/managementRequests/details/MrInformation.vue';
import { useNotifications } from '@/store/modules/notifications';
import DetailsView from '@/components/ui/DetailsView.vue';
import { RouteName } from '@/global';
import TitledView from '@/components/ui/TitledView.vue';

/**
 * Wrapper component for a certification service view
 */
export default defineComponent({
  name: 'ManagementRequestDetails',
  components: {
    TitledView,
    DetailsView,
    MrInformation,
    MrSecurityServerInformation,
    MrClientInformation,
    MrCertificateInformation,
    MrConfirmDialog,
    MrDeclineDialog,
  },
  props: {
    requestId: {
      type: Number,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      backTo: {
        name: RouteName.ManagementRequests,
      },
    };
  },
  computed: {
    ...mapStores(useManagementRequests),

    managementRequest(): ManagementRequestDetailedView | null {
      return this.managementRequestsStore.currentManagementRequest;
    },
    typeText(): string {
      return managementTypeToText(
        this.managementRequestsStore.currentManagementRequest?.type,
      );
    },
    hasCertificateInfo(): boolean {
      if (!this.managementRequestsStore.currentManagementRequest) {
        return false;
      }

      return [
        ManagementRequestType.AUTH_CERT_DELETION_REQUEST,
        ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST,
      ].includes(this.managementRequestsStore.currentManagementRequest.type);
    },
    hasClientInfo(): boolean {
      if (!this.managementRequestsStore.currentManagementRequest) {
        return false;
      }

      return [
        ManagementRequestType.CLIENT_DELETION_REQUEST,
        ManagementRequestType.CLIENT_REGISTRATION_REQUEST,
        ManagementRequestType.CLIENT_DISABLE_REQUEST,
        ManagementRequestType.CLIENT_ENABLE_REQUEST,
        ManagementRequestType.OWNER_CHANGE_REQUEST,
      ].includes(this.managementRequestsStore.currentManagementRequest.type);
    },
  },
  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    fetchData: async function () {
      this.loading = true;
      try {
        await this.managementRequestsStore.loadById(this.requestId);
      } catch (error: unknown) {
        this.showError(error);
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>
<style lang="scss" scoped>
@import '@/assets/tables';
@import '@/assets/colors';

.management-request-additional-details {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-start;

  margin-bottom: 24px;

  .details-block {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }

    &:last-child {
      margin-left: 30px;
    }
  }
}
</style>
