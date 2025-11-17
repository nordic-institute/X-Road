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
  <XrdView data-test="system-parameters-tab-view" title="tab.main.settings">
    <template #tabs>
      <SettingsTabs />
    </template>
    <XrdSubView class="settings-subview">
      <template #header>
        <v-spacer />
        <MaintenanceModeWidget class="mr-1" />
      </template>
      <XrdCard
        v-if="hasPermission(permissions.CHANGE_SS_ADDRESS)"
        title="systemParameters.securityServer.securityServer"
        class="settings-block"
      >
        <v-table class="xrd">
          <thead>
            <tr>
              <th>
                {{ $t('systemParameters.securityServer.serverAddress') }}
              </th>
              <th></th>
              <th></th>
            </tr>
          </thead>
          <tbody data-test="system-parameters-server-address-table-body">
            <tr>
              <td>{{ serverAddress }}</td>
              <td class="text-end">
                <div v-if="addressChangeInProgress" class="status-wrapper">
                  <XrdStatusChip
                    :type="'info'"
                    text="systemParameters.securityServer.addressChangeInProgress"
                  >
                    <template #icon>
                      <XrdStatusIcon
                        class="mr-1 ml-n1"
                        status="progress-register"
                      />
                    </template>
                  </XrdStatusChip>
                </div>
              </td>
              <td class="text-end">
                <XrdBtn
                  data-test="change-server-address-button"
                  variant="text"
                  text="action.edit"
                  color="tertiary"
                  :disabled="addressChangeInProgress"
                  @click="showEditServerAddressDialog = true"
                />
              </td>
            </tr>
          </tbody>
        </v-table>
      </XrdCard>

      <XrdCard
        v-if="hasPermission(permissions.VIEW_ANCHOR)"
        title="systemParameters.configurationAnchor.title"
        class="settings-block"
      >
        <template #title-actions>
          <div class="d-flex flex-row align-center justify-end">
            <XrdBtn
              v-if="hasPermission(permissions.DOWNLOAD_ANCHOR)"
              data-test="system-parameters-configuration-anchor-download-button"
              variant="text"
              text="systemParameters.configurationAnchor.action.download"
              prepend-icon="download"
              color="tertiary"
              :loading="downloadingAnchor"
              @click="downloadAnchor"
            />

            <UploadConfigurationAnchorDialog
              @uploaded="fetchConfigurationAnchor"
            />
          </div>
        </template>
        <v-table class="xrd">
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
          <tbody data-test="system-parameters-configuration-anchor-table-body">
            <tr v-if="configurationAnchor">
              <td>
                <XrdHashValue :value="configurationAnchor.hash" />
              </td>
              <td class="text-left">
                <XrdDateTime :value="configurationAnchor.created_at" />
              </td>
            </tr>

            <XrdEmptyPlaceholderRow
              :colspan="2"
              :loading="loadingAnchor"
              :data="configurationAnchor"
              :no-items-text="$t('noData.noTimestampingServices')"
            />
          </tbody>
        </v-table>
      </XrdCard>

      <XrdCard
        v-if="hasPermission(permissions.VIEW_TSPS)"
        title="systemParameters.timestampingServices.title"
        class="settings-block"
        :class="{ 'ts-disabled': !messageLogEnabled }"
      >
        <template #title-actions>
          <template
            v-if="hasPermission(permissions.ADD_TSP) && messageLogEnabled"
          >
            <AddTimestampingServiceDialog
              :configured-timestamping-services="configuredTimestampingServices"
              @added="fetchConfiguredTimestampingServiced"
            />
          </template>
          <template v-if="!messageLogEnabled">
            <XrdStatusChip
              type="inactive"
              text="diagnostics.addOnStatus.messageLogDisabled"
            />
          </template>
        </template>
        <span class="pl-4" :class="{ 'opacity-60': !messageLogEnabled }">
          {{
            $t(
              'systemParameters.servicePrioritizationStrategy.timestamping.label',
            )
          }}
          <strong data-test="timestamping-prioritization-strategy">{{
            timestampingPrioritizationStrategy
          }}</strong>
          {{ ' - ' }}
          {{
            $t(
              `systemParameters.servicePrioritizationStrategy.timestamping.${timestampingPrioritizationStrategy}`,
            )
          }}
        </span>
        <v-table class="xrd">
          <thead>
            <tr>
              <th :class="{ 'opacity-60': !messageLogEnabled }">
                {{
                  $t(
                    'systemParameters.timestampingServices.table.header.timestampingService',
                  )
                }}
              </th>
              <th :class="{ 'opacity-60': !messageLogEnabled }">
                {{
                  $t(
                    'systemParameters.timestampingServices.table.header.serviceURL',
                  )
                }}
              </th>
              <th :class="{ 'opacity-60': !messageLogEnabled }">
                {{
                  $t(
                    'systemParameters.timestampingServices.table.header.costType',
                  )
                }}
              </th>
              <th>&nbsp;</th>
            </tr>
          </thead>
          <tbody data-test="system-parameters-timestamping-services-table-body">
            <TimestampingServiceRow
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
        </v-table>
      </XrdCard>
      <XrdCard
        v-if="hasPermission(permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)"
        title="systemParameters.approvedCertificateAuthorities.title"
        class="settings-block"
        :class="{ 'ts-disabled': !messageLogEnabled }"
      >
        <span class="pl-4">
          {{
            $t('systemParameters.servicePrioritizationStrategy.ocsp.label')
          }}
          <strong data-test="ocsp-prioritization-strategy">{{ ocspPrioritizationStrategy }}</strong>
          {{ ' - ' }}
          {{
            $t(
              `systemParameters.servicePrioritizationStrategy.ocsp.${ocspPrioritizationStrategy}`,
            )
          }}
        </span>
        <v-table class="xrd">
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
                    'systemParameters.approvedCertificateAuthorities.table.header.acmeIpAddresses',
                  )
                }}
              </th>
              <th>
                {{
                  $t(
                    'systemParameters.approvedCertificateAuthorities.table.header.ocspUrl',
                  )
                }}
              </th>
              <th>
                {{
                  $t(
                    'systemParameters.approvedCertificateAuthorities.table.header.ocspCostType',
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
              data-test="system-parameters-approved-ca-row"
            >
              <td
                :class="{
                  'interm-ca': !approvedCA.top_ca,
                  'root-ca': approvedCA.top_ca,
                }"
              >
                {{ approvedCA.subject_distinguished_name }}
              </td>
              <td
                v-if="
                  approvedCA.acme_server_ip_addresses &&
                  approvedCA.acme_server_ip_addresses.length > 0
                "
              >
                <p
                  v-for="ipAddress in approvedCA.acme_server_ip_addresses"
                  :key="ipAddress"
                >
                  {{ ipAddress }}
                </p>
              </td>
              <td v-else>
                {{
                  $t(
                    'systemParameters.approvedCertificateAuthorities.table.notAvailable',
                  )
                }}
              </td>
              <td>
                <div
                  class="py-2"
                  v-for="ocspResponder in approvedCA.ocsp_responders"
                  :key="ocspResponder.url"
                >
                  <p>
                    {{ ocspResponder.url }}
                  </p>
                </div>
              </td>
              <td>
                <div
                  class="py-2"
                  v-for="ocspResponder in approvedCA.ocsp_responders"
                  :key="ocspResponder.url"
                >
                  <p>
                    {{
                      $t(
                        'systemParameters.costType.' +
                        ocspResponder.cost_type,
                      )
                    }}
                  </p>
                </div>
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
                <XrdDate :value="approvedCA.not_after" />
              </td>
            </tr>

            <XrdEmptyPlaceholderRow
              :colspan="4"
              :loading="loadingCAs"
              :data="orderedCertificateAuthorities"
              :no-items-text="$t('noData.noCertificateAuthorities')"
            />
          </tbody>
        </v-table>
      </XrdCard>
    </XrdSubView>
    <EditSecurityServerAddressDialog
      v-if="showEditServerAddressDialog"
      :address="serverAddress!"
      @cancel="showEditServerAddressDialog = false"
      @address-updated="addressChangeSubmitted"
    />
  </XrdView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import {
  XrdBtn,
  XrdDateTime,
  XrdDate,
  XrdHashValue,
  XrdView,
  XrdCard,
  XrdSubView,
  XrdStatusChip,
  helper,
  useNotifications,
  XrdEmptyPlaceholderRow,
  XrdStatusIcon,
} from '@niis/shared-ui';
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
import AddTimestampingServiceDialog from '@/views/Settings/SystemParameters/AddTimestampingServiceDialog.vue';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import EditSecurityServerAddressDialog from '@/views/Settings/SystemParameters/EditSecurityServerAddressDialog.vue';
import { sortTimestampingServices } from '@/util/sorting';
import MaintenanceModeWidget from '@/views/Settings/SystemParameters/MaintenanceModeWidget.vue';
import SettingsTabs from '@/views/Settings/SettingsTabs.vue';

export default defineComponent({
  components: {
    XrdStatusIcon,
    SettingsTabs,
    MaintenanceModeWidget,
    EditSecurityServerAddressDialog,
    XrdBtn,
    TimestampingServiceRow,
    UploadConfigurationAnchorDialog,
    AddTimestampingServiceDialog,
    XrdDateTime,
    XrdDate,
    XrdHashValue,
    XrdView,
    XrdCard,
    XrdSubView,
    XrdStatusChip,
    XrdEmptyPlaceholderRow,
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      configurationAnchor: undefined as Anchor | undefined,
      downloadingAnchor: false,
      configuredTimestampingServices: [] as TimestampingService[],
      timestampingPrioritizationStrategy: undefined as
        | ServicePrioritizationStrategy
        | undefined,
      certificateAuthorities: [] as CertificateAuthority[],
      ocspPrioritizationStrategy: undefined as
        | ServicePrioritizationStrategy
        | undefined,
      permissions: Permissions,
      loadingTimestampingservices: false,
      loadingAnchor: false,
      loadingCAs: false,
      loadingMessageLogEnabled: false,
      messageLogEnabled: false,
      showEditServerAddressDialog: false,
      addressChangeInProgress: false,
      serverAddress: '',
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
      this.fetchTimestampingPrioritizationStrategy();
    }

    if (this.hasPermission(Permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)) {
      this.fetchApprovedCertificateAuthorities();
      this.fetchOcspPrioritizationStrategy();
    }
    if (this.hasPermission(Permissions.CHANGE_SS_ADDRESS)) {
      this.fetchServerAddress();
    }
  },
  methods: {
    async fetchConfigurationAnchor() {
      this.loadingAnchor = true;
      return api
        .get<Anchor>('/system/anchor')
        .then((resp) => (this.configurationAnchor = resp.data))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingAnchor = false));
    },
    async fetchMessageLogEnabled() {
      this.loadingMessageLogEnabled = true;
      return api
        .get<AddOnStatus>('/diagnostics/addon-status')
        .then((resp) => (this.messageLogEnabled = resp.data.messagelog_enabled))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingMessageLogEnabled = false));
    },
    async fetchConfiguredTimestampingServiced() {
      this.loadingTimestampingservices = true;
      return api
        .get<TimestampingService[]>('/system/timestamping-services')
        .then(
          (resp) =>
            (this.configuredTimestampingServices = sortTimestampingServices(
              resp.data,
            )),
        )
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingTimestampingservices = false));
    },
    async fetchTimestampingPrioritizationStrategy() {
      return api
        .get<ServicePrioritizationStrategy>(
          '/system/timestamping-services/prioritization-strategy',
        )
        .then((resp) => (this.timestampingPrioritizationStrategy = resp.data))
        .catch((error) => this.showError(error));
    },
    async fetchApprovedCertificateAuthorities() {
      this.loadingCAs = true;
      return api
        .get<CertificateAuthority[]>(
          '/certificate-authorities?include_intermediate_cas=true',
        )
        .then((resp) => (this.certificateAuthorities = resp.data))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingCAs = false));
    },
    async fetchOcspPrioritizationStrategy() {
      return api
        .get<ServicePrioritizationStrategy>(
          '/certificate-authorities/ocsp-prioritization-strategy',
        )
        .then((resp) => (this.ocspPrioritizationStrategy = resp.data))
        .catch((error) => this.showError(error));
    },
    downloadAnchor(): void {
      this.downloadingAnchor = true;
      api
        .get('/system/anchor/download', { responseType: 'blob' })
        .then((res) =>
          helper.saveResponseAsFile(res, 'configuration-anchor.xml'),
        )
        .catch((error) => this.addError(error))
        .finally(() => (this.downloadingAnchor = false));
    },
    fetchServerAddress(): boolean {
      if (this.hasPermission(Permissions.CHANGE_SS_ADDRESS)) {
        api
          .get<SecurityServerAddressStatus>('/system/server-address')
          .then((resp) => {
            this.serverAddress = resp.data.current_address?.address || '';
            this.addressChangeInProgress =
              resp.data.requested_change !== undefined;
          })
          .catch((error) => this.addError(error));
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
.ts-disabled {
  :deep(.v-card-title),
  :deep(.v-table__wrapper) {
    background-color: rgba(var(--v-theme-on-surface-variant), 0.08) !important;
  }

  :deep(.component-title-text) {
    opacity: 0.6;
  }
}

.settings-block:not(:last-child) {
  margin-bottom: 16px;
}

.vertical-align-top {
  vertical-align: top;
}
</style>
