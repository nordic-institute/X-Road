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
  <main id="management-request-details" class="mt-0">
    <div class="navigation-back">
      <router-link to="/management-requests">
        <v-icon>mdi-chevron-left</v-icon>
        {{ $t('global.navigation.back') }}
      </router-link>
    </div>
    <!-- Title  -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title align-fix">
        {{
          this.managementRequestsStore.currentManagementRequest.type ===
          'AUTH_CERT_REGISTRATION_REQUEST'
            ? $t('managementRequests.details.authCertRegistrationDetail')
            : this.managementRequestsStore.currentManagementRequest.type ===
              'AUTH_CERT_DELETION_REQUEST'
            ? $t('managementRequests.details.authCertDeletionDetail')
            : this.managementRequestsStore.currentManagementRequest.type ===
              'CLIENT_REGISTRATION_REQUEST'
            ? $t('managementRequests.details.clientRegistrationDetail')
            : this.managementRequestsStore.currentManagementRequest.type ===
              'CLIENT_DELETION_REQUEST'
            ? $t('managementRequests.details.clientDeletionDetail')
            : this.managementRequestsStore.currentManagementRequest.type ===
              'OWNER_CHANGE_REQUEST'
            ? $t('managementRequests.details.ownerChangeDetail')
            : ''
        }}
      </div>
      <div class="card-corner-button pr-4">
        <xrd-button
          class="mr-4"
          v-if="
            this.managementRequestsStore.currentManagementRequest.status ===
            'WAITING'
          "
          outlined
          @click="openDeclineConfirmationDialog()"
        >
          {{ $t('action.decline') }}</xrd-button
        >
        <xrd-button
          v-if="
            this.managementRequestsStore.currentManagementRequest.status ===
            'WAITING'
          "
          outlined
          @click="openApproveConfirmationDialog()"
        >
          {{ $t('action.approve') }}</xrd-button
        >
      </div>
    </div>

    <div>
      <!-- Request Information -->
      <div class="mb-6">
        <v-card class="pb-4" flat>
          <div class="card-top">
            <div class="card-main-title">
              {{ $t('managementRequests.details.requestInfo.requestInfo') }}
            </div>
          </div>

          <table class="xrd-table mt-0 pb-3">
            <tbody>
              <tr>
                <td class="title-cell">
                  <div>
                    <div>
                      {{
                        $t('managementRequests.details.requestInfo.requestId')
                      }}
                    </div>
                  </div>
                </td>
                <td>
                  {{ managementRequestsStore.currentManagementRequest.id }}
                </td>
              </tr>
              <tr>
                <td class="title-cell">
                  <div>
                    <div>
                      {{
                        $t('managementRequests.details.requestInfo.received')
                      }}
                    </div>
                  </div>
                </td>
                <td>
                  {{
                    managementRequestsStore.currentManagementRequest.created_at
                      | formatDateTime
                  }}
                </td>
              </tr>
              <tr>
                <td class="title-cell">
                  <div>
                    <div>
                      {{ $t('managementRequests.details.requestInfo.source') }}
                    </div>
                  </div>
                </td>
                <td>
                  {{ managementRequestsStore.currentManagementRequest.origin }}
                </td>
              </tr>
              <tr>
                <td class="title-cell">
                  <div>
                    <div>
                      {{ $t('managementRequests.details.requestInfo.status') }}
                    </div>
                  </div>
                </td>
                <td>
                  {{ managementRequestsStore.currentManagementRequest.status }}
                </td>
              </tr>
              <tr>
                <td class="title-cell">
                  <div>
                    <div>
                      {{
                        $t('managementRequests.details.requestInfo.comments')
                      }}
                    </div>
                  </div>
                </td>
                <td></td>
              </tr>
            </tbody>
          </table>
        </v-card>
      </div>
    </div>
    <div id="ss-client-details">
      <!-- SS Information -->
      <v-card class="details-card">
        <div class="card-top">
          <div class="card-main-title">
            {{ $t('managementRequests.details.affectedServerInfo') }}
          </div>
        </div>

        <table class="xrd-table mt-0 pb-3">
          <tbody>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.ownerName') }}
                  </div>
                </div>
              </td>
              <td>
                {{ managementRequestsStore.currentManagementRequest
                .security_server_owner }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.ownerClass') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  managementRequestsStore.currentManagementRequest
                    .security_server_id | getClass
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.ownerCode') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  managementRequestsStore.currentManagementRequest
                    .security_server_id | getCode
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.serverCode') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  managementRequestsStore.currentManagementRequest
                    .security_server_id | getSubsystem
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.address') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  managementRequestsStore.currentManagementRequest.serverAddress
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </v-card>
      <!-- Client Information -->
      <v-card
        v-if="
          this.managementRequestsStore.currentManagementRequest.type ===
            'CLIENT_REGISTRATION_REQUEST' ||
          this.managementRequestsStore.currentManagementRequest.type ===
            'CLIENT_DELETION_REQUEST' ||
          this.managementRequestsStore.currentManagementRequest.type ===
            'OWNER_CHANGE_REQUEST'
        "
        class="details-card"
      >
        <div class="card-top">
          <div class="card-main-title">
            {{
              this.managementRequestsStore.currentManagementRequest.type ===
              'CLIENT_REGISTRATION_REQUEST'
                ? $t('managementRequests.details.clientRegistrationSubmit')
                : this.managementRequestsStore.currentManagementRequest.type ===
                  'CLIENT_DELETION_REQUEST'
                ? $t('managementRequests.details.clientDeletionSubmit')
                : this.managementRequestsStore.currentManagementRequest.type ===
                  'OWNER_CHANGE_REQUEST'
                ? $t('managementRequests.details.ownerChangeSubmit')
                : ''
            }}
          </div>
        </div>

        <table class="xrd-table mt-0 pb-3">
          <tbody>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.ownerName') }}
                  </div>
                </div>
              </td>
              <td>
                {{ managementRequestsStore.currentManagementRequest.client_owner }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.ownerClass') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  managementRequestsStore.currentManagementRequest.clientId
                    | getClass
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.ownerCode') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  managementRequestsStore.currentManagementRequest.clientId
                    | getCode
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('securityServers.subsystemCode') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  managementRequestsStore.currentManagementRequest.clientId
                    | getSubsystem
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div />
                <div />
              </td>
            </tr>
          </tbody>
        </table>
      </v-card>

      <!-- Certificate Information -->
      <v-card
        v-if="
          this.managementRequestsStore.currentManagementRequest.type ===
            'AUTH_CERT_REGISTRATION_REQUEST' ||
          this.managementRequestsStore.currentManagementRequest.type ===
            'AUTH_CERT_DELETION_REQUEST'
        "
        class="details-card"
      >
        <div class="card-top">
          <div class="card-main-title">
            {{
              this.managementRequestsStore.currentManagementRequest.type ===
              'AUTH_CERT_REGISTRATION_REQUEST'
                ? $t('managementRequests.details.authCertRegistrationSubmit')
                : this.managementRequestsStore.currentManagementRequest.type ===
                  'AUTH_CERT_DELETION_REQUEST'
                ? $t('managementRequests.details.authCertDeletionSubmit')
                : ''
            }}
          </div>
        </div>

        <table class="xrd-table mt-0 pb-3">
          <tbody>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('managementRequests.details.authCertInfo.ca') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  this.managementRequestsStore.currentManagementRequest.certificateDetails
                    .issuer_common_name
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{
                      $t('managementRequests.details.authCertInfo.serialNumber')
                    }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  this.managementRequestsStore
                    .currentManagementRequest.certificateDetails.serial
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('managementRequests.details.authCertInfo.subject') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  this.managementRequestsStore
                    .currentManagementRequest.certificateDetails
                    .subject_distinguished_name
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('managementRequests.details.authCertInfo.expires') }}
                  </div>
                </div>
              </td>
              <td>
                {{
                  this.managementRequestsStore
                    .currentManagementRequest.certificateDetails.not_after
                    | formatDateTime
                }}
              </td>
            </tr>
            <tr>
              <td class="title-cell">
                <div />
                <div />
              </td>
            </tr>
          </tbody>
        </table>
      </v-card>
    </div>

    <!-- Confirm decline dialog -->
    <xrd-confirm-dialog
      v-if="confirmApprove"
      :dialog="confirmApprove"
      title="managementRequests.approveRequest"
      text="managementRequests.confirmApprove"
      :data="{
        id: this.managementRequestsStore.currentManagementRequest.id,
        serverId:
          this.managementRequestsStore.currentManagementRequest
            .security_server_id,
      }"
      :loading="approvingRequest"
      @cancel="confirmApprove = false"
      @accept="approve()"
    />
    <!-- Confirm decline dialog -->
    <xrd-confirm-dialog
      v-if="confirmDecline"
      :dialog="confirmDecline"
      title="managementRequests.declineRequest"
      text="managementRequests.confirmDecline"
      :data="{
        id: this.managementRequestsStore.currentManagementRequest.id,
        serverId:
          this.managementRequestsStore.currentManagementRequest
            .security_server_id,
      }"
      :loading="decliningRequest"
      @cancel="confirmDecline = false"
      @accept="decline()"
    />
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapStores } from 'pinia';
import { managementRequestsStore } from '@/store/modules/managementRequestStore';

/**
 * Component for a Management Request details view
 */
export default Vue.extend({
  name: 'ManagementRequestDetails',
  components: {},
  computed: {
    ...mapStores(managementRequestsStore),
  },
  props: {
    managementRequestId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirmApprove: false,
      approvingRequest: false,
      confirmDecline: false,
      decliningRequest: false,
    };
  },
  created() {
    this.managementRequestsStore.loadDetails(this.managementRequestId);
  },
  methods: {
    openDeclineConfirmationDialog(): void {
      this.confirmDecline = true;
    },
    openApproveConfirmationDialog(): void {
      this.confirmApprove = true;
    },
    approve: async function () {
      await this.managementRequestsStore.approve(
        this.managementRequestsStore.currentManagementRequest.id as number,
      );
    },
    decline: function () {
      this.managementRequestsStore.decline(
        this.managementRequestsStore.currentManagementRequest.id as number,
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.navigation-back {
  color: $XRoad-Link;
  cursor: pointer;
  margin-bottom: 20px;
  a {
    text-decoration: none;
  }
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.title-cell {
  max-width: 40%;
  width: 40%;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}

#ss-client-details {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  .details-card {
    width: 100%;

    &:first-child {
      margin-right: 10px;
    }

    &:last-child {
      margin-left: 24px;
    }
  }
}
</style>
