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
  <div class="mt-3" data-test="system-parameters-tab-view">
    <div class="xrd-view-title pb-6">{{ $t('systemParameters.title') }}</div>

    <v-card flat class="xrd-card" v-if="hasPermission(permissions.CHANGE_SS_ADDRESS)">
      <v-card-text class="card-text">
        <v-row no-gutters class="px-4">
          <v-col>
            <h3>{{ $t('systemParameters.securityServer.securityServer') }}</h3>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <table class="xrd-table">
              <thead>
                <tr>
                  <th>{{ $t('systemParameters.securityServer.serverAddress') }}</th>
                  <th></th>
                  <th></th>
                </tr>
              </thead>
              <tbody data-test="system-parameters-server-address-table-body">
                <tr>
                  <td>{{ serverAddress }}</td>
                  <td>
                    <div v-if="addressChangeInProgress" class="status-wrapper" >
                      <xrd-status-icon :status="'progress-register'"/>
                      <div class="status-text">{{ $t('systemParameters.securityServer.addressChangeInProgress') }}</div>
                    </div>
                  </td>
                  <td class="pr-4">
                    <xrd-button :outlined="false"
                        data-test="change-server-address-button"
                        text
                        :disabled="addressChangeInProgress"
                        @click="showEditServerAddressDialog = true">
                      {{ $t('action.edit') }}
                    </xrd-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
    <v-card flat class="xrd-card">
      <v-card-text class="card-text">
        <v-row
          v-if="hasPermission(permissions.VIEW_ANCHOR)"
          no-gutters
          class="px-4"
        >
          <v-col
            ><h3>
              {{ $t('systemParameters.configurationAnchor.title') }}
            </h3></v-col
          >
          <v-col class="text-right">
            <div class="anchor-buttons">
              <xrd-button
                v-if="hasPermission(permissions.DOWNLOAD_ANCHOR)"
                data-test="system-parameters-configuration-anchor-download-button"
                :loading="downloadingAnchor"
                outlined
                @click="downloadAnchor"
              >
                <xrd-icon-base class="xrd-large-button-icon">
                  <xrd-icon-download />
                </xrd-icon-base>
                {{ $t('systemParameters.configurationAnchor.action.download') }}
              </xrd-button>

              <upload-configuration-anchor-dialog
                @uploaded="fetchConfigurationAnchor"
              />
            </div>
          </v-col>
        </v-row>
        <v-row v-if="hasPermission(permissions.VIEW_ANCHOR)" no-gutters>
          <v-col>
            <table class="xrd-table">
              <thead>
                <tr>
                  <th>
                    {{
                      $t(
                        'systemParameters.configurationAnchor.table.header.distinguishedName',
                      )
                    }}
                  </th>
                  <th>
                    {{
                      $t(
                        'systemParameters.configurationAnchor.table.header.generated',
                      )
                    }}
                  </th>
                </tr>
              </thead>
              <tbody
                data-test="system-parameters-configuration-anchor-table-body"
              >
                <tr v-if="configurationAnchor">
                  <td>{{ $filters.colonize(configurationAnchor.hash) }}</td>
                  <td class="pr-4">
                    {{ $filters.formatDateTime(configurationAnchor.created_at) }}
                  </td>
                </tr>

                <XrdEmptyPlaceholderRow
                  :colspan="2"
                  :loading="loadingAnchor"
                  :data="configurationAnchor"
                  :no-items-text="$t('noData.noTimestampingServices')"
                />
              </tbody>
            </table>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
    <v-card flat class="xrd-card" :class="{ disabled: !messageLogEnabled }">
      <v-card-text class="card-text">
        <v-row
          v-if="hasPermission(permissions.VIEW_TSPS)"
          no-gutters
          class="px-4"
        >
          <v-col
            ><h3 :class="{ disabled: !messageLogEnabled }">
              {{ $t('systemParameters.timestampingServices.title') }}
            </h3></v-col
          >
          <v-col
            v-if="hasPermission(permissions.ADD_TSP) && messageLogEnabled"
            class="text-right"
          >
            <add-timestamping-service-dialog
              :configured-timestamping-services="configuredTimestampingServices"
              @added="fetchConfiguredTimestampingServiced"
            />
          </v-col>
          <v-col v-if="!messageLogEnabled" class="text-right disabled">
            {{ $t('diagnostics.addOnStatus.messageLogDisabled') }}
          </v-col>
        </v-row>

        <v-row v-if="hasPermission(permissions.VIEW_TSPS)" no-gutters>
          <v-col>
            <table class="xrd-table">
              <thead>
                <tr>
                  <th>
                    {{
                      $t(
                        'systemParameters.timestampingServices.table.header.timestampingService',
                      )
                    }}
                  </th>
                  <th>
                    {{
                      $t(
                        'systemParameters.timestampingServices.table.header.serviceURL',
                      )
                    }}
                  </th>
                  <th>&nbsp;</th>
                </tr>
              </thead>
              <tbody
                data-test="system-parameters-timestamping-services-table-body"
              >
                <timestamping-service-row
                  v-for="timestampingService in configuredTimestampingServices"
                  :key="timestampingService.url"
                  :timestamping-service="timestampingService"
                  :message-log-enabled="messageLogEnabled"
                  @deleted="fetchConfiguredTimestampingServiced"
                />

                <XrdEmptyPlaceholderRow
                  :colspan="3"
                  :loading="loadingTimestampingservices"
                  :data="configuredTimestampingServices"
                  :no-items-text="$t('noData.noTimestampingServices')"
                />
              </tbody>
            </table>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
    <v-card flat class="xrd-card">
      <v-card-text class="card-text">
        <v-row
          v-if="
            hasPermission(permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)
          "
          no-gutters
          class="px-4"
        >
          <v-col
            ><h3>
              {{ $t('systemParameters.approvedCertificateAuthorities.title') }}
            </h3></v-col
          >
        </v-row>
        <v-row
          v-if="
            hasPermission(permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)
          "
          no-gutters
        >
          <v-col>
            <table class="xrd-table">
              <thead>
                <tr>
                  <th>
                    {{
                      $t(
                        'systemParameters.approvedCertificateAuthorities.table.header.distinguishedName',
                      )
                    }}
                  </th>
                  <th>
                    {{
                      $t(
                        'systemParameters.approvedCertificateAuthorities.table.header.ocspResponse',
                      )
                    }}
                  </th>
                  <th>
                    {{
                      $t(
                        'systemParameters.approvedCertificateAuthorities.table.header.expires',
                      )
                    }}
                  </th>
                </tr>
              </thead>
              <tbody data-test="system-parameters-approved-ca-table-body">
                <tr
                  v-for="approvedCA in orderedCertificateAuthorities"
                  :key="approvedCA.path"
                >
                  <td
                    :class="{
                      'interm-ca': !approvedCA.top_ca,
                      'root-ca': approvedCA.top_ca,
                    }"
                  >
                    {{ approvedCA.subject_distinguished_name }}
                  </td>
                  <td v-if="approvedCA.top_ca">
                    {{
                      $t(
                        'systemParameters.approvedCertificateAuthorities.table.ocspResponse.NOT_AVAILABLE',
                      )
                    }}
                  </td>
                  <td v-if="!approvedCA.top_ca">
                    {{
                      $t(
                        `systemParameters.approvedCertificateAuthorities.table.ocspResponse.${approvedCA.ocsp_response}`,
                      )
                    }}
                  </td>
                  <td class="pr-4">
                    {{ $filters.formatDate(approvedCA.not_after) }}
                  </td>
                </tr>

                <XrdEmptyPlaceholderRow
                  :colspan="4"
                  :loading="loadingCAs"
                  :data="orderedCertificateAuthorities"
                  :no-items-text="$t('noData.noCertificateAuthorities')"
                />
              </tbody>
            </table>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
  </div>

  <edit-security-server-address-dialog
    v-if="showEditServerAddressDialog"
    :address="serverAddress!"
    @cancel="showEditServerAddressDialog = false"
    @address-updated="addressChangeSubmitted"
  />

</template>

<script lang="ts">
import { defineComponent } from 'vue';
import {
  AddOnStatus,
  Anchor,
  CertificateAuthority,
  SecurityServerAddressStatus,
  TimestampingService,
} from '@/openapi-types';
import * as api from '@/util/api';
import { Permissions } from '@/global';
import TimestampingServiceRow from '@/views/Settings/SystemParameters/TimestampingServiceRow.vue';
import UploadConfigurationAnchorDialog from '@/views/Settings/SystemParameters/UploadConfigurationAnchorDialog.vue';
import { saveResponseAsFile } from '@/util/helpers';
import AddTimestampingServiceDialog from '@/views/Settings/SystemParameters/AddTimestampingServiceDialog.vue';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { XrdButton, XrdIconDownload } from '@niis/shared-ui';
import EditSecurityServerAddressDialog from "@/views/Settings/SystemParameters/EditSecurityServerAddressDialog.vue";

export default defineComponent({
  components: {
    EditSecurityServerAddressDialog,
    XrdButton,
    XrdIconDownload,
    TimestampingServiceRow,
    UploadConfigurationAnchorDialog,
    AddTimestampingServiceDialog,
  },
  data() {
    return {
      configurationAnchor: undefined as Anchor | undefined,
      downloadingAnchor: false,
      configuredTimestampingServices: [] as TimestampingService[],
      certificateAuthorities: [] as CertificateAuthority[],
      permissions: Permissions,
      loadingTimestampingservices: false,
      loadingAnchor: false,
      loadingCAs: false,
      loadingMessageLogEnabled: false,
      messageLogEnabled: false,
      showEditServerAddressDialog: false,
      addressChangeInProgress: false,
      serverAddress: "",
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission', 'currentSecurityServer']),
    orderedCertificateAuthorities(): CertificateAuthority[] {
      const temp = this.certificateAuthorities;

      return temp.sort((authorityA, authorityB) =>
        authorityA.path.localeCompare(authorityB.path),
      );
    },
  },
  created(): void {
    if (this.hasPermission(Permissions.VIEW_ANCHOR)) {
      this.fetchConfigurationAnchor();
    }

    if (this.hasPermission(Permissions.VIEW_TSPS)) {
      this.fetchMessageLogEnabled();
      this.fetchConfiguredTimestampingServiced();
    }

    if (this.hasPermission(Permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)) {
      this.fetchApprovedCertificateAuthorities();
    }
    if (this.hasPermission(Permissions.CHANGE_SS_ADDRESS)) {
      this.fetchServerAddress();
    }
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),

    async fetchConfigurationAnchor() {
      this.loadingAnchor = true;
      return api
        .get<Anchor>('/system/anchor')
        .then((resp) => (this.configurationAnchor = resp.data))
        .catch((error) => this.showError(error))
        .finally(() => (this.loadingAnchor = false));
    },
    async fetchMessageLogEnabled() {
      this.loadingMessageLogEnabled = true;
      return api
        .get<AddOnStatus>('/diagnostics/addon-status')
        .then((resp) => (this.messageLogEnabled = resp.data.messagelog_enabled))
        .catch((error) => this.showError(error))
        .finally(() => (this.loadingMessageLogEnabled = false));
    },
    async fetchConfiguredTimestampingServiced() {
      this.loadingTimestampingservices = true;
      return api
        .get<TimestampingService[]>('/system/timestamping-services')
        .then((resp) => (this.configuredTimestampingServices = resp.data))
        .catch((error) => this.showError(error))
        .finally(() => (this.loadingTimestampingservices = false));
    },
    async fetchApprovedCertificateAuthorities() {
      this.loadingCAs = true;
      return api
        .get<CertificateAuthority[]>(
          '/certificate-authorities?include_intermediate_cas=true',
        )
        .then((resp) => (this.certificateAuthorities = resp.data))
        .catch((error) => this.showError(error))
        .finally(() => (this.loadingCAs = false));
    },
    downloadAnchor(): void {
      this.downloadingAnchor = true;
      api
        .get('/system/anchor/download', { responseType: 'blob' })
        .then((res) => saveResponseAsFile(res, 'configuration-anchor.xml'))
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => (this.downloadingAnchor = false));
    },
    fetchServerAddress(): boolean {
      if (this.hasPermission(Permissions.CHANGE_SS_ADDRESS)) {
        api
            .get<SecurityServerAddressStatus>('/system/server-address')
            .then((resp) => {
              this.serverAddress = resp.data.current_address?.address!;
              this.addressChangeInProgress = resp.data.requested_change !== undefined;
            })
            .catch((error) => this.showError(error))
      }
      return false;
    },
    addressChangeSubmitted(): void {
      this.showEditServerAddressDialog = false;
      this.addressChangeInProgress = true;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

h3 {
  color: $XRoad-Black100;
  font-size: 18px;
  font-weight: bold;
  letter-spacing: 0;
  line-height: 24px;
}

.card-text {
  padding-left: 0;
  padding-right: 0;
}

.disabled {
  cursor: not-allowed;
  background: $XRoad-Black10;
  color: $XRoad-WarmGrey100;
}

tr td {
  color: $XRoad-Black100;
  font-weight: normal !important;
}

tr td:last-child {
  width: 1%;
  white-space: nowrap;
}

.root-ca {
  font-weight: bold !important;
}

.interm-ca {
  font-weight: normal !important;
  padding-left: 2rem !important;
}

.xrd-card {
  margin-bottom: 24px;
}

.anchor-buttons {
  display: flex;
  justify-content: flex-end;
}
.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}
.status-text {
  font-style: normal;
  font-weight: bold;
  font-size: 12px;
  line-height: 16px;
  color: $XRoad-WarmGrey100;
  margin-left: 2px;
}
</style>
