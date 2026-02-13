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
  <XrdCard
    id="management-services-anchor"
    data-test="system-settings-system-parameters-card"
    title="systemSettings.managementServices.title"
    :loading="loading"
  >
    <XrdCardTable>
      <XrdCardTableRow
        data-test="management-service-provider-identifier-field"
        label="systemSettings.serviceProviderIdentifier"
        :value="managementServicesConfiguration.service_provider_id"
      >
        <XrdBtn
          v-if="hasPermissionToEditServiceProvider"
          data-test="edit-management-subsystem"
          variant="text"
          text="action.edit"
          color="tertiary"
          @click="openSelectSubsystemDialog"
        />
      </XrdCardTableRow>
      <XrdCardTableRow
        data-test="management-service-provider-name-field"
        label="systemSettings.serviceProviderName"
        :value="managementServicesConfiguration.service_provider_name"
      />
      <XrdCardTableRow
        data-test="management-security-server-field"
        label="systemSettings.managementServices.securityServer"
        :value="managementServicesConfiguration.security_server_id"
      >
        <XrdBtn
          v-if="canEditSecurityServer"
          data-test="edit-management-security-server"
          variant="text"
          text="action.edit"
          color="tertiary"
          @click="openSelectSecurityServerDialog"
        />
      </XrdCardTableRow>
      <XrdCardTableRow
        data-test="management-wsdl-address-field"
        label="systemSettings.wsdlAddress"
        :value="managementServicesConfiguration.wsdl_address"
      >
        <XrdBtn
          v-if="managementServicesConfiguration.wsdl_address"
          data-test="management-wsdl-address-copy-btn"
          variant="text"
          text="action.copy"
          color="tertiary"
          @click.prevent="copyUrl(managementServicesConfiguration.wsdl_address)"
        />
      </XrdCardTableRow>
      <XrdCardTableRow
        data-test="management-management-services-address-field"
        label="systemSettings.managementServicesAddress"
        :value="managementServicesConfiguration.services_address"
      >
        <XrdBtn
          v-if="managementServicesConfiguration.services_address"
          data-test="management-management-services-address-copy-btn"
          variant="text"
          text="action.copy"
          color="tertiary"
          @click.prevent="copyUrl(managementServicesConfiguration.services_address)"
        />
      </XrdCardTableRow>
      <XrdCardTableRow
        data-test="management-owner-group-code-field"
        label="systemSettings.securityServerOwnerGroupCode"
        :value="managementServicesConfiguration.security_server_owners_global_group_code"
      />
    </XrdCardTable>
    <SelectSubsystemDialog
      v-if="showSelectSubsystemDialog"
      :current-subsystem-id="managementServicesConfiguration.service_provider_id || ''"
      @select="showSelectSubsystemDialog = false"
      @cancel="showSelectSubsystemDialog = false"
    >
    </SelectSubsystemDialog>
    <SelectSecurityServerDialog
      v-if="showSelectSecurityServerDialog"
      :current-security-server="managementServicesConfiguration.security_server_id"
      @select="showSelectSecurityServerDialog = false"
      @cancel="showSelectSecurityServerDialog = false"
    />
  </XrdCard>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapState, mapStores } from 'pinia';

import { XrdBtn, XrdCard, XrdCardTable, XrdCardTableRow } from '@niis/shared-ui';

import { Permissions } from '@/global';
import { ManagementServicesConfiguration } from '@/openapi-types';
import { useManagementServices } from '@/store/modules/management-services';
import { useUser } from '@/store/modules/user';

import SelectSecurityServerDialog from './SelectSecurityServerDialog.vue';
import SelectSubsystemDialog from './SelectSubsystemDialog.vue';

export default defineComponent({
  components: {
    XrdCardTableRow,
    XrdBtn,
    XrdCard,
    XrdCardTable,
    SelectSubsystemDialog,
    SelectSecurityServerDialog,
  },
  data() {
    return {
      loading: false,
      showSelectSubsystemDialog: false,
      showSelectSecurityServerDialog: false,
    };
  },
  computed: {
    ...mapStores(useManagementServices),
    ...mapState(useUser, ['hasPermission']),
    managementServicesConfiguration(): ManagementServicesConfiguration {
      return this.managementServicesStore.managementServicesConfiguration;
    },
    hasPermissionToEditServiceProvider(): boolean {
      return this.hasPermission(Permissions.VIEW_SYSTEM_SETTINGS);
    },
    canEditSecurityServer(): boolean {
      return (
        this.isServiceProviderSelected() &&
        this.isServiceProviderUnRegistered() &&
        this.hasPermission(Permissions.REGISTER_SERVICE_PROVIDER)
      );
    },
  },
  created() {
    this.fetchManagementServicesConfiguration();
  },
  methods: {
    fetchManagementServicesConfiguration(): void {
      this.loading = true;
      this.managementServicesStore.fetchManagementServicesConfiguration().finally(() => (this.loading = false));
    },
    copyUrl(url: string): void {
      if (url) {
        navigator.clipboard.writeText(url);
      }
    },
    openSelectSubsystemDialog(): void {
      this.showSelectSubsystemDialog = true;
    },
    hideSelectSubsystemDialog(): void {
      this.showSelectSubsystemDialog = false;
    },
    isServiceProviderSelected(): boolean {
      return this.managementServicesStore.managementServicesConfiguration.service_provider_id !== undefined;
    },
    isServiceProviderUnRegistered(): boolean {
      return this.managementServicesStore.managementServicesConfiguration.security_server_id === ``;
    },
    openSelectSecurityServerDialog(): void {
      this.showSelectSecurityServerDialog = true;
    },
  },
});
</script>
