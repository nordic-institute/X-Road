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
                  <XrdStatusChip :type="'info'" text="systemParameters.securityServer.addressChangeInProgress">
                    <template #icon>
                      <XrdStatusIcon class="mr-1 ml-n1" status="progress-register" />
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

      <XrdCard v-if="hasPermission(permissions.VIEW_ANCHOR)" title="systemParameters.configurationAnchor.title" class="settings-block">
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

            <UploadConfigurationAnchorDialog @uploaded="fetchConfigurationAnchor" />
          </div>
        </template>
        <v-table class="xrd">
          <thead>
            <tr>
              <th>
                {{ $t('systemParameters.configurationAnchor.table.header.distinguishedName') }}
              </th>
              <th>
                {{ $t('systemParameters.configurationAnchor.table.header.generated') }}
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
          <template v-if="hasPermission(permissions.ADD_TSP) && messageLogEnabled">
            <AddTimestampingServiceDialog
              :configured-timestamping-services="configuredTimestampingServices"
              @added="fetchConfiguredTimestampingServiced"
            />
          </template>
          <template v-if="!messageLogEnabled">
            <XrdStatusChip type="inactive" text="diagnostics.addOnStatus.messageLogDisabled" />
          </template>
        </template>
        <span class="pl-4" :class="{ 'opacity-60': !messageLogEnabled }">
          {{ $t('systemParameters.servicePrioritizationStrategy.timestamping.label') }}
          <strong data-test="timestamping-prioritization-strategy">{{ timestampingPrioritizationStrategy }}</strong>
          {{ separator }}
          {{ $t(`systemParameters.servicePrioritizationStrategy.timestamping.${timestampingPrioritizationStrategy}`) }}
        </span>
        <v-table class="xrd">
          <thead>
            <tr>
              <th :class="{ 'opacity-60': !messageLogEnabled }">
                {{ $t('systemParameters.timestampingServices.table.header.timestampingService') }}
              </th>
              <th :class="{ 'opacity-60': !messageLogEnabled }">
                {{ $t('systemParameters.timestampingServices.table.header.serviceURL') }}
              </th>
              <th :class="{ 'opacity-60': !messageLogEnabled }">
                {{ $t('systemParameters.timestampingServices.table.header.costType') }}
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
          {{ $t('systemParameters.servicePrioritizationStrategy.ocsp.label') }}
          <strong data-test="ocsp-prioritization-strategy">{{ ocspPrioritizationStrategy }}</strong>
          {{ separator }}
          {{ $t(`systemParameters.servicePrioritizationStrategy.ocsp.${ocspPrioritizationStrategy}`) }}
        </span>
        <v-table class="xrd">
          <thead>
            <tr>
              <th>
                {{ $t('systemParameters.approvedCertificateAuthorities.table.header.distinguishedName') }}
              </th>
              <th>
                {{ $t('systemParameters.approvedCertificateAuthorities.table.header.acmeIpAddresses') }}
              </th>
              <th>
                {{ $t('systemParameters.approvedCertificateAuthorities.table.header.ocspUrl') }}
              </th>
              <th>
                {{ $t('systemParameters.approvedCertificateAuthorities.table.header.ocspCostType') }}
              </th>
              <th>
                {{ $t('systemParameters.approvedCertificateAuthorities.table.header.ocspResponse') }}
              </th>
              <th>
                {{ $t('systemParameters.approvedCertificateAuthorities.table.header.expires') }}
              </th>
            </tr>
          </thead>
          <tbody data-test="system-parameters-approved-ca-table-body">
            <tr v-for="approvedCA in orderedCertificateAuthorities" :key="approvedCA.path" data-test="system-parameters-approved-ca-row">
              <td
                :class="{
                  'interm-ca': !approvedCA.top_ca,
                  'root-ca': approvedCA.top_ca,
                }"
              >
                {{ approvedCA.subject_distinguished_name }}
              </td>
              <td v-if="approvedCA.acme_server_ip_addresses && approvedCA.acme_server_ip_addresses.length > 0">
                <p v-for="ipAddress in approvedCA.acme_server_ip_addresses" :key="ipAddress">
                  {{ ipAddress }}
                </p>
              </td>
              <td v-else>
                {{ $t('systemParameters.approvedCertificateAuthorities.table.notAvailable') }}
              </td>
              <td>
                <div v-for="ocspResponder in approvedCA.ocsp_responders" :key="ocspResponder.url">
                  <p>
                    {{ ocspResponder.url }}
                  </p>
                </div>
              </td>
              <td>
                <div v-for="ocspResponder in approvedCA.ocsp_responders" :key="ocspResponder.url">
                  <p>
                    {{ $t('systemParameters.costType.' + ocspResponder.cost_type) }}
                  </p>
                </div>
              </td>
              <td v-if="approvedCA.top_ca">
                {{ $t('systemParameters.approvedCertificateAuthorities.table.ocspResponse.NOT_AVAILABLE') }}
              </td>
              <td v-if="!approvedCA.top_ca">
                {{ $t(`systemParameters.approvedCertificateAuthorities.table.ocspResponse.${approvedCA.ocsp_response}`) }}
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

      <XrdCard
        v-if="hasPermission(permissions.CHANGE_CONFIGURATION_PROPERTY)"
        title="systemParameters.configurableProperties.title"
        class="settings-block"
      >
        <v-alert
          v-if="modifiedScopes.length > 0"
          class="ma-4"
          type="warning"
          variant="outlined"
          border="start"
          density="compact"
          data-test="configurable-properties-restart-warning"
        >
          {{ $t('systemParameters.configurableProperties.restartWarning', { scopes: modifiedScopes.join(', ') }) }}
        </v-alert>

        <div class="px-4">
          <XrdRoundedSearchField
            v-model="propertySearch"
            data-test="configurable-properties-search"
            autofocus
            :label="$t('systemParameters.configurableProperties.search')" />
        </div>

        <XrdEmptyPlaceholder
          class="px-4"
          :data="Object.keys(filteredPropertiesByScope)"
          :filtered="propertySearch.length > 0"
          :loading="loadingProperties"
          :no-items-text="$t('noData.noConfigurableProperties')"
        />

        <div v-if="!loadingProperties && Object.keys(filteredPropertiesByScope).length > 0" class="mt-3 mx-4 border xrd-rounded-12 pa-0" data-test="configurable-properties-panels">
          <div
            v-for="(scopeProperties, scope, index) in filteredPropertiesByScope"
            :key="scope"
            :class="{ 'border-b': index < Object.keys(filteredPropertiesByScope).length - 1 }"
            :data-test="`configurable-properties-panel-${scope}`"
          >
            <div
              class="cursor-pointer d-flex flex-row align-center pt-2 pb-2 pl-4 pr-4"
              :data-test="`configurable-properties-panel-title-${scope}`"
              @click="toggleScope(String(scope))"
            >
              <v-btn
                class="xrd opacity-100"
                variant="plain"
                color="primary"
                :icon="isScopeOpen(String(scope)) ? 'keyboard_arrow_down' : 'chevron_right'"
                :ripple="false"
              />
              <span class="font-weight-medium text-capitalize">{{ scope }}</span>
            </div>
            <v-slide-y-transition>
              <v-table v-if="isScopeOpen(String(scope))" class="xrd configurable-properties-table">
                <colgroup>
                  <col style="width: 25%" />
                  <col style="width: 13%" />
                  <col style="width: 13%" />
                  <col style="width: 39%" />
                  <col style="width: 10%" />
                </colgroup>
                <thead>
                  <tr>
                    <th>{{ $t('systemParameters.configurableProperties.table.header.propertyName') }}</th>
                    <th>{{ $t('systemParameters.configurableProperties.table.header.currentValue') }}</th>
                    <th>{{ $t('systemParameters.configurableProperties.table.header.defaultValue') }}</th>
                    <th>{{ $t('systemParameters.configurableProperties.table.header.description') }}</th>
                  </tr>
                </thead>
                <tbody :data-test="`configurable-properties-table-body-${scope}`">
                  <tr
                    v-for="prop in scopeProperties"
                    :key="prop.property_name"
                    data-test="configurable-property-row"
                  >
                    <td class="property-name-cell">{{ prop.property_name }}</td>
                    <td class="property-value-cell">{{ prop.current_value ?? '-' }}</td>
                    <td class="property-value-cell">{{ prop.default_value || '-' }}</td>
                    <td class="property-description-cell">{{ getPropertyDescription(prop.property_name) }}</td>
                    <td>
                      <div class="d-flex align-center justify-end">
                        <v-tooltip v-if="modifiedProperties.includes(prop.property_name!)" open-delay="500">
                          <template #activator="{ props: tooltipProps }">
                            <v-icon v-bind="tooltipProps" icon="warning" color="warning" class="mr-2" />
                          </template>
                          {{ $t('systemParameters.configurableProperties.propertyRestartWarning') }}
                        </v-tooltip>
                        <XrdBtn
                          data-test="edit-configurable-property-button"
                          variant="text"
                          text="action.edit"
                          color="tertiary"
                          @click="openEditDialog(prop)"
                        />
                      </div>
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </v-slide-y-transition>
          </div>
        </div>
      </XrdCard>
    </XrdSubView>
    <EditSecurityServerAddressDialog
      v-if="showEditServerAddressDialog"
      :address="serverAddress!"
      @cancel="showEditServerAddressDialog = false"
      @address-updated="addressChangeSubmitted"
    />
    <EditConfigurablePropertyDialog
      v-if="editingProperty"
      :property="editingProperty"
      @cancel="editingProperty = undefined"
      @saved="onPropertySaved"
    />
  </XrdView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import {
  saveResponseAsFile,
  useNotifications,
  XrdBtn,
  XrdCard,
  XrdDate,
  XrdDateTime,
  XrdEmptyPlaceholder,
  XrdEmptyPlaceholderRow,
  XrdHashValue,
  XrdStatusChip,
  XrdStatusIcon,
  XrdSubView,
  XrdView,
} from '@niis/shared-ui';
import { Anchor, CertificateAuthority, ServicePrioritizationStrategy, TimestampingService, SecurityServerConfigurableProperty } from '@/openapi-types';
import { Permissions } from '@/global';
import TimestampingServiceRow from '@/views/Settings/SystemParameters/TimestampingServiceRow.vue';
import UploadConfigurationAnchorDialog from '@/views/Settings/SystemParameters/UploadConfigurationAnchorDialog.vue';
import AddTimestampingServiceDialog from '@/views/Settings/SystemParameters/AddTimestampingServiceDialog.vue';
import EditConfigurablePropertyDialog from '@/views/Settings/SystemParameters/EditConfigurablePropertyDialog.vue';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import EditSecurityServerAddressDialog from '@/views/Settings/SystemParameters/EditSecurityServerAddressDialog.vue';
import MaintenanceModeWidget from '@/views/Settings/SystemParameters/MaintenanceModeWidget.vue';
import SettingsTabs from '@/views/Settings/SettingsTabs.vue';
import { useSystem } from '@/store/modules/system';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { useTimestampingServices } from '@/store/modules/timestamping-services';
import { useCsr } from '@/store/modules/certificateSignRequest';

export default defineComponent({
  components: {
    XrdStatusIcon,
    XrdEmptyPlaceholder,
    SettingsTabs,
    MaintenanceModeWidget,
    EditSecurityServerAddressDialog,
    EditConfigurablePropertyDialog,
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
    const {
      fetchConfigurationAnchor: apiFetchConfigurationAnchor,
      downloadAnchor: apiDownloadAnchor,
      fetchSecurityServerAddress,
      fetchConfigurableProperties: apiFetchConfigurableProperties,
    } = useSystem();
    const { fetchAddonStatus } = useDiagnostics();
    const { fetchSortedTimestampingServiced, fetchTimestampingPrioritizationStrategy: apiFetchTimestampingPrioritizationStrategy } =
      useTimestampingServices();
    const { searchCertificateAuthorities, fetchCertificateAuthoritiesPrioritizationStrategy } = useCsr();
    return {
      addError,
      apiFetchConfigurationAnchor,
      fetchAddonStatus,
      fetchSortedTimestampingServiced,
      apiFetchTimestampingPrioritizationStrategy,
      searchCertificateAuthorities,
      fetchCertificateAuthoritiesPrioritizationStrategy,
      apiDownloadAnchor,
      fetchSecurityServerAddress,
      apiFetchConfigurableProperties,
    };
  },
  data() {
    return {
      configurationAnchor: undefined as Anchor | undefined,
      downloadingAnchor: false,
      configuredTimestampingServices: [] as TimestampingService[],
      timestampingPrioritizationStrategy: undefined as ServicePrioritizationStrategy | undefined,
      certificateAuthorities: [] as CertificateAuthority[],
      ocspPrioritizationStrategy: undefined as ServicePrioritizationStrategy | undefined,
      permissions: Permissions,
      loadingTimestampingservices: false,
      loadingAnchor: false,
      loadingCAs: false,
      loadingMessageLogEnabled: false,
      messageLogEnabled: false,
      showEditServerAddressDialog: false,
      addressChangeInProgress: false,
      serverAddress: '',
      separator: ' - ',
      configurableProperties: [] as SecurityServerConfigurableProperty[],
      loadingProperties: false,
      editingProperty: undefined as SecurityServerConfigurableProperty | undefined,
      modifiedScopes: [] as string[],
      modifiedProperties: [] as string[],
      openScopes: {} as Record<string, boolean>,
      propertySearch: '',
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission', 'currentSecurityServer']),
    orderedCertificateAuthorities(): CertificateAuthority[] {
      const temp = this.certificateAuthorities;

      return temp.sort((authorityA, authorityB) => authorityA.path.localeCompare(authorityB.path));
    },
    propertiesByScope(): Record<string, SecurityServerConfigurableProperty[]> {
      const result: Record<string, SecurityServerConfigurableProperty[]> = {};
      for (const prop of this.configurableProperties) {
        const scope = prop.scope || 'common';
        if (!result[scope]) result[scope] = [];
        result[scope].push(prop);
      }
      for (const scope of Object.keys(result)) {
        result[scope].sort((a, b) => (a.property_name ?? '').localeCompare(b.property_name ?? ''));
      }
      return result;
    },
    filteredPropertiesByScope(): Record<string, SecurityServerConfigurableProperty[]> {
      const term = this.propertySearch.trim().toLowerCase();
      if (!term) return this.propertiesByScope;

      const result: Record<string, SecurityServerConfigurableProperty[]> = {};
      for (const [scope, props] of Object.entries(this.propertiesByScope)) {
        const matched = props.filter((p) => p.property_name?.toLowerCase().includes(term));
        if (matched.length > 0) {
          result[scope] = matched;
        }
      }
      return result;
    },
  },
  watch: {
    filteredPropertiesByScope(filtered: Record<string, SecurityServerConfigurableProperty[]>) {
      if (!this.propertySearch.trim()) return;
      for (const scope of Object.keys(filtered)) {
        this.openScopes[scope] = true;
      }
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
    if (this.hasPermission(Permissions.CHANGE_CONFIGURATION_PROPERTY)) {
      this.fetchConfigurablePropertiesList();
    }
  },
  methods: {
    async fetchConfigurationAnchor() {
      this.loadingAnchor = true;
      return this.apiFetchConfigurationAnchor()
        .then((data) => (this.configurationAnchor = data))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingAnchor = false));
    },
    async fetchMessageLogEnabled() {
      this.loadingMessageLogEnabled = true;
      return this.fetchAddonStatus()
        .then((data) => (this.messageLogEnabled = data.messagelog_enabled))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingMessageLogEnabled = false));
    },
    async fetchConfiguredTimestampingServiced() {
      this.loadingTimestampingservices = true;
      return this.fetchSortedTimestampingServiced()
        .then((sorted) => (this.configuredTimestampingServices = sorted))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingTimestampingservices = false));
    },
    async fetchTimestampingPrioritizationStrategy() {
      return this.apiFetchTimestampingPrioritizationStrategy()
        .then((data) => (this.timestampingPrioritizationStrategy = data))
        .catch((error) => this.addError(error));
    },
    async fetchApprovedCertificateAuthorities() {
      this.loadingCAs = true;
      return this.searchCertificateAuthorities(true)
        .then((data) => (this.certificateAuthorities = data))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingCAs = false));
    },
    async fetchOcspPrioritizationStrategy() {
      return this.fetchCertificateAuthoritiesPrioritizationStrategy()
        .then((data) => (this.ocspPrioritizationStrategy = data))
        .catch((error) => this.addError(error));
    },
    downloadAnchor(): void {
      this.downloadingAnchor = true;
      this.apiDownloadAnchor()
        .then((res) => saveResponseAsFile(res, 'configuration-anchor.xml'))
        .catch((error) => this.addError(error))
        .finally(() => (this.downloadingAnchor = false));
    },
    fetchServerAddress(): boolean {
      if (this.hasPermission(Permissions.CHANGE_SS_ADDRESS)) {
        this.fetchSecurityServerAddress()
          .then((data) => {
            this.serverAddress = data.current_address?.address || '';
            this.addressChangeInProgress = data.requested_change !== undefined;
          })
          .catch((error) => this.addError(error));
      }
      return false;
    },
    addressChangeSubmitted(): void {
      this.showEditServerAddressDialog = false;
      this.addressChangeInProgress = true;
    },
    async fetchConfigurablePropertiesList() {
      this.loadingProperties = true;
      return this.apiFetchConfigurableProperties()
        .then((data) => (this.configurableProperties = data))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingProperties = false));
    },
    getPropertyDescription(propertyName: string | undefined): string {
      if (!propertyName) return '-';
      const key = 'systemParameters.configurableProperties.descriptions.' + propertyName;
      return this.$te(key) ? String(this.$t(key)) : '-';
    },
    openEditDialog(prop: SecurityServerConfigurableProperty): void {
      this.editingProperty = prop;
    },
    isScopeOpen(scope: string): boolean {
      return this.openScopes[scope];
    },
    toggleScope(scope: string): void {
      this.openScopes[scope] = !this.isScopeOpen(scope);
    },
    onPropertySaved(scope: string | undefined): void {
      const propertyName = this.editingProperty?.property_name;
      this.editingProperty = undefined;
      if (scope && !this.modifiedScopes.includes(scope)) {
        this.modifiedScopes.push(scope);
      }
      if (propertyName && !this.modifiedProperties.includes(propertyName)) {
        this.modifiedProperties.push(propertyName);
      }
      this.fetchConfigurablePropertiesList();
    }
  },
});
</script>

<style lang="scss" scoped>
.ts-disabled {
  :deep(.v-card-title),
  :deep(.v-table__wrapper) {
    background-color: color-mix(in srgb, rgb(var(--v-theme-on-surface-variant)) 8%, transparent) !important;
  }

  :deep(.component-title-text) {
    opacity: 0.6;
  }
}

.settings-block:not(:last-child) {
  margin-bottom: 16px;
}

:deep(.configurable-properties-table) {
  table {
    table-layout: fixed;
    width: 100%;
  }

  td {
    overflow-wrap: break-word;
    word-break: break-word;
  }
}
</style>
