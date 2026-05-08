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
        v-if="hasPermission(Permissions.CHANGE_SS_ADDRESS)"
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

      <XrdCard v-if="hasPermission(Permissions.VIEW_ANCHOR)" title="systemParameters.configurationAnchor.title" class="settings-block">
        <template #title-actions>
          <div class="d-flex flex-row align-center justify-end">
            <XrdBtn
              v-if="hasPermission(Permissions.DOWNLOAD_ANCHOR)"
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
        v-if="hasPermission(Permissions.VIEW_TSPS)"
        title="systemParameters.timestampingServices.title"
        class="settings-block"
        :class="{ 'ts-disabled': !messageLogEnabled }"
      >
        <template #title-actions>
          <template v-if="hasPermission(Permissions.ADD_TSP) && messageLogEnabled">
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
          {{ ' - ' }}
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
        v-if="hasPermission(Permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)"
        title="systemParameters.approvedCertificateAuthorities.title"
        class="settings-block"
        :class="{ 'ts-disabled': !messageLogEnabled }"
      >
        <span class="pl-4">
          {{ $t('systemParameters.servicePrioritizationStrategy.ocsp.label') }}
          <strong data-test="ocsp-prioritization-strategy">{{ ocspPrioritizationStrategy }}</strong>
          {{ ' - ' }}
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
        v-if="hasPermission(Permissions.CHANGE_CONFIGURATION_PROPERTY)"
        title="systemParameters.configurableProperties.title"
        class="settings-block"
      >
        <template #title-actions>
          <XrdBtn
            v-if="hasAnyOpenScope"
            data-test="configurable-properties-collapse-all"
            variant="text"
            text="systemParameters.configurableProperties.collapseAll"
            prepend-icon="keyboard_arrow_up"
            color="tertiary"
            @click="collapseAllScopes"
          />
        </template>
        <v-alert
          v-if="modifiedScopes.size > 0"
          class="ma-4"
          type="warning"
          variant="outlined"
          border="start"
          density="compact"
          data-test="configurable-properties-restart-warning"
        >
          {{ $t('systemParameters.configurableProperties.restartWarning', { scopes: [...modifiedScopes].join(', ') }) }}
        </v-alert>

        <div class="px-4">
          <XrdRoundedSearchField
            v-model="propertySearch"
            data-test="configurable-properties-search"
            autofocus
            :label="$t('systemParameters.configurableProperties.search')"
          />
        </div>

        <XrdEmptyPlaceholder
          class="px-4"
          :data="filteredScopeKeys"
          :filtered="propertySearch.length > 0"
          :loading="loadingProperties"
          :no-items-text="$t('noData.noConfigurableProperties')"
        />

        <div v-if="!loadingProperties && filteredScopeKeys.length > 0" class="mt-3 mx-4 mb-4" data-test="configurable-properties-panels">
          <ScopePropertiesExpandable
            v-for="(scope, index) in filteredScopeKeys"
            :key="scope"
            :class="{ 'mb-4': index < filteredScopeKeys.length - 1 }"
            :scope="scope"
            :properties="filteredPropertiesByScope[scope]"
            :modified-properties="modifiedProperties"
            :is-open="openScopes[scope] ?? false"
            @open="openScopes[scope] = $event"
            @edit-property="editingProperty = $event"
          />
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

<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
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
import type {
  Anchor,
  CertificateAuthority,
  SecurityServerConfigurableProperty,
  ServicePrioritizationStrategy,
  TimestampingService,
} from '@/openapi-types';
import { Permissions } from '@/global';
import TimestampingServiceRow from '@/views/Settings/SystemParameters/TimestampingServiceRow.vue';
import UploadConfigurationAnchorDialog from '@/views/Settings/SystemParameters/UploadConfigurationAnchorDialog.vue';
import AddTimestampingServiceDialog from '@/views/Settings/SystemParameters/AddTimestampingServiceDialog.vue';
import EditConfigurablePropertyDialog from '@/views/Settings/SystemParameters/EditConfigurablePropertyDialog.vue';
import ScopePropertiesExpandable from '@/views/Settings/SystemParameters/ScopePropertiesExpandable.vue';
import { useUser } from '@/store/modules/user';
import EditSecurityServerAddressDialog from '@/views/Settings/SystemParameters/EditSecurityServerAddressDialog.vue';
import MaintenanceModeWidget from '@/views/Settings/SystemParameters/MaintenanceModeWidget.vue';
import SettingsTabs from '@/views/Settings/SettingsTabs.vue';
import { useSystem } from '@/store/modules/system';
import { useDiagnostics } from '@/store/modules/diagnostics';
import { useTimestampingServices } from '@/store/modules/timestamping-services';
import { useCsr } from '@/store/modules/certificateSignRequest';

const { addError } = useNotifications();
const {
  fetchConfigurationAnchor: apiFetchConfigurationAnchor,
  downloadAnchor: apiDownloadAnchor,
  fetchSecurityServerAddress,
  fetchConfigurableProperties,
} = useSystem();
const { fetchAddonStatus } = useDiagnostics();
const { fetchSortedTimestampingServiced, fetchTimestampingPrioritizationStrategy: apiFetchTimestampingPrioritizationStrategy } =
  useTimestampingServices();
const { searchCertificateAuthorities, fetchCertificateAuthoritiesPrioritizationStrategy } = useCsr();
const { hasPermission } = useUser();

const configurationAnchor = ref<Anchor | undefined>(undefined);
const downloadingAnchor = ref(false);
const configuredTimestampingServices = ref<TimestampingService[]>([]);
const timestampingPrioritizationStrategy = ref<ServicePrioritizationStrategy | undefined>(undefined);
const certificateAuthorities = ref<CertificateAuthority[]>([]);
const ocspPrioritizationStrategy = ref<ServicePrioritizationStrategy | undefined>(undefined);
const loadingTimestampingservices = ref(false);
const loadingAnchor = ref(false);
const loadingCAs = ref(false);
const loadingMessageLogEnabled = ref(false);
const messageLogEnabled = ref(false);
const showEditServerAddressDialog = ref(false);
const addressChangeInProgress = ref(false);
const serverAddress = ref('');
const configurableProperties = ref<SecurityServerConfigurableProperty[]>([]);
const loadingProperties = ref(false);
const editingProperty = ref<SecurityServerConfigurableProperty | undefined>(undefined);
const modifiedScopes = ref<Set<string>>(new Set());
const modifiedProperties = ref<Set<string>>(new Set());
const openScopes = ref<Record<string, boolean>>({});
const propertySearch = ref('');

const orderedCertificateAuthorities = computed<CertificateAuthority[]>(() =>
  [...certificateAuthorities.value].sort((a, b) => a.path.localeCompare(b.path)),
);

const propertiesByScope = computed<Record<string, SecurityServerConfigurableProperty[]>>(() => {
  const result: Record<string, SecurityServerConfigurableProperty[]> = {};
  for (const prop of configurableProperties.value) {
    const scope = prop.scope || 'common';
    if (!result[scope]) result[scope] = [];
    result[scope].push(prop);
  }
  for (const scope of Object.keys(result)) {
    result[scope].sort((a, b) => (a.property_name ?? '').localeCompare(b.property_name ?? ''));
  }
  return result;
});

const filteredPropertiesByScope = computed<Record<string, SecurityServerConfigurableProperty[]>>(() => {
  const term = propertySearch.value.trim().toLowerCase();
  if (!term) return propertiesByScope.value;

  const result: Record<string, SecurityServerConfigurableProperty[]> = {};
  for (const [scope, props] of Object.entries(propertiesByScope.value)) {
    const matched = props.filter((p) => p.property_name?.toLowerCase().includes(term));
    if (matched.length > 0) result[scope] = matched;
  }
  return result;
});

const filteredScopeKeys = computed(() =>
  Object.keys(filteredPropertiesByScope.value).sort((a, b) => (a === 'common' ? -1 : b === 'common' ? 1 : a.localeCompare(b))),
);

const hasAnyOpenScope = computed(() => filteredScopeKeys.value.some((scope) => openScopes.value[scope]));

watch(filteredPropertiesByScope, (filtered) => {
  if (!propertySearch.value.trim()) return;
  for (const scope of Object.keys(filtered)) {
    openScopes.value[scope] = true;
  }
});

async function fetchConfigurationAnchor() {
  loadingAnchor.value = true;
  return apiFetchConfigurationAnchor()
    .then((data) => (configurationAnchor.value = data))
    .catch((error) => addError(error))
    .finally(() => (loadingAnchor.value = false));
}

async function fetchMessageLogEnabled() {
  loadingMessageLogEnabled.value = true;
  return fetchAddonStatus()
    .then((data) => (messageLogEnabled.value = data.messagelog_enabled))
    .catch((error) => addError(error))
    .finally(() => (loadingMessageLogEnabled.value = false));
}

async function fetchConfiguredTimestampingServiced() {
  loadingTimestampingservices.value = true;
  return fetchSortedTimestampingServiced()
    .then((sorted) => (configuredTimestampingServices.value = sorted))
    .catch((error) => addError(error))
    .finally(() => (loadingTimestampingservices.value = false));
}

async function fetchTimestampingPrioritizationStrategy() {
  return apiFetchTimestampingPrioritizationStrategy()
    .then((data) => (timestampingPrioritizationStrategy.value = data))
    .catch((error) => addError(error));
}

async function fetchApprovedCertificateAuthorities() {
  loadingCAs.value = true;
  return searchCertificateAuthorities(true)
    .then((data) => (certificateAuthorities.value = data))
    .catch((error) => addError(error))
    .finally(() => (loadingCAs.value = false));
}

async function fetchOcspPrioritizationStrategy() {
  return fetchCertificateAuthoritiesPrioritizationStrategy()
    .then((data) => (ocspPrioritizationStrategy.value = data))
    .catch((error) => addError(error));
}

function downloadAnchor(): void {
  downloadingAnchor.value = true;
  apiDownloadAnchor()
    .then((res) => saveResponseAsFile(res, 'configuration-anchor.xml'))
    .catch((error) => addError(error))
    .finally(() => (downloadingAnchor.value = false));
}

function fetchServerAddress(): void {
  fetchSecurityServerAddress()
    .then((data) => {
      serverAddress.value = data.current_address?.address || '';
      addressChangeInProgress.value = data.requested_change !== undefined;
    })
    .catch((error) => addError(error));
}

function addressChangeSubmitted(): void {
  showEditServerAddressDialog.value = false;
  addressChangeInProgress.value = true;
}

async function fetchConfigurablePropertiesList() {
  loadingProperties.value = true;
  return fetchConfigurableProperties()
    .then((data) => (configurableProperties.value = data))
    .catch((error) => addError(error))
    .finally(() => (loadingProperties.value = false));
}

function collapseAllScopes(): void {
  for (const scope of filteredScopeKeys.value) {
    openScopes.value[scope] = false;
  }
}

function onPropertySaved(scope: string): void {
  const propertyName = editingProperty.value?.property_name;
  editingProperty.value = undefined;
  modifiedScopes.value.add(scope);
  if (propertyName) modifiedProperties.value.add(propertyName);
  fetchConfigurablePropertiesList();
}

if (hasPermission(Permissions.VIEW_ANCHOR)) {
  fetchConfigurationAnchor();
}

if (hasPermission(Permissions.VIEW_TSPS)) {
  fetchMessageLogEnabled();
  fetchConfiguredTimestampingServiced();
  fetchTimestampingPrioritizationStrategy();
}

if (hasPermission(Permissions.VIEW_APPROVED_CERTIFICATE_AUTHORITIES)) {
  fetchApprovedCertificateAuthorities();
  fetchOcspPrioritizationStrategy();
}

if (hasPermission(Permissions.CHANGE_SS_ADDRESS)) {
  fetchServerAddress();
}

if (hasPermission(Permissions.CHANGE_CONFIGURATION_PROPERTY)) {
  fetchConfigurablePropertiesList();
}
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
</style>
