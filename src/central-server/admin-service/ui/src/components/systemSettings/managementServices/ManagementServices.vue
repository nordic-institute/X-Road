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
  <div id="management-services-anchor" class="mb-6">
    <v-card class="pb-4" flat>
      <div class="card-top">
        <div class="card-main-title">
          {{ $t('systemSettings.managementServices.title') }}
        </div>
      </div>

      <table class="xrd-table mt-0 pb-3">
        <xrd-empty-placeholder-row
          :colspan="2"
          :loading="loading"
          :data="managementServicesConfiguration"
          :no-items-text="$t('noData.noData')"
        />

        <tbody v-if="!loading">
          <tr>
            <td class="title-cell">
              {{ $t('systemSettings.serviceProviderIdentifier') }}
            </td>
            <td data-test="management-service-provider-identifier-field">
              {{ managementServicesConfiguration.service_provider_id }}
            </td>
            <td class="action-cell">
              <xrd-button
                v-if="hasPermissionToEditServiceProvider"
                text
                :outlined="false"
                data-test="edit-management-subsystem"
                @click="openSelectSubsystemDialog"
                >{{ $t('action.edit') }}
              </xrd-button>
            </td>
          </tr>

          <tr>
            <td>
              {{ $t('systemSettings.serviceProviderName') }}
            </td>
            <td data-test="management-service-provider-name-field">
              {{ managementServicesConfiguration.service_provider_name }}
            </td>
            <td></td>
          </tr>

          <tr>
            <td>
              {{ $t('systemSettings.managementServices.securityServer') }}
            </td>
            <td data-test="management-security-server-field">
              {{ managementServicesConfiguration.security_server_id }}
            </td>
            <td class="action-cell">
              <xrd-button
                v-if="canEditSecurityServer"
                text
                data-test="edit-management-security-server"
                @click="openSelectSecurityServerDialog"
                >{{ $t('action.edit') }}
              </xrd-button>
            </td>
          </tr>

          <tr>
            <td>
              {{ $t('systemSettings.wsdlAddress') }}
            </td>
            <td data-test="management-wsdl-address-field">
              {{ managementServicesConfiguration.wsdl_address }}
            </td>
            <td class="action-cell">
              <xrd-button
                v-if="managementServicesConfiguration.wsdl_address"
                text
                :outlined="false"
                class="copy-button"
                data-test="management-wsdl-address-copy-btn"
                @click.prevent="
                  copyUrl(managementServicesConfiguration.wsdl_address)
                "
              >
                <v-icon class="xrd-large-button-icon" icon="icon-copy" />
                {{ $t('action.copy') }}
              </xrd-button>
            </td>
          </tr>

          <tr>
            <td>
              {{ $t('systemSettings.managementServicesAddress') }}
            </td>
            <td data-test="management-management-services-address-field">
              {{ managementServicesConfiguration.services_address }}
            </td>
            <td class="action-cell">
              <xrd-button
                v-if="managementServicesConfiguration.services_address"
                text
                :outlined="false"
                class="copy-button"
                data-test="management-management-services-address-copy-btn"
                @click.prevent="
                  copyUrl(managementServicesConfiguration.services_address)
                "
              >
                <v-icon class="xrd-large-button-icon" icon="icon-copy" />
                {{ $t('action.copy') }}
              </xrd-button>
            </td>
          </tr>

          <tr>
            <td>
              {{ $t('systemSettings.securityServerOwnerGroupCode') }}
            </td>
            <td data-test="management-owner-group-code-field">
              {{
                managementServicesConfiguration.security_server_owners_global_group_code
              }}
            </td>
            <td></td>
          </tr>
        </tbody>
      </table>
    </v-card>
    <select-subsystem-dialog
      v-if="showSelectSubsystemDialog"
      :current-subsystem-id="
        managementServicesConfiguration.service_provider_id || ''
      "
      @select="showSelectSubsystemDialog = false"
      @cancel="showSelectSubsystemDialog = false"
    >
    </select-subsystem-dialog>
    <select-security-server-dialog
      v-if="showSelectSecurityServerDialog"
      :current-security-server="
        managementServicesConfiguration.security_server_id
      "
      @select="showSelectSecurityServerDialog = false"
      @cancel="showSelectSecurityServerDialog = false"
    />
  </div>
</template>

<script lang="ts">
import { ManagementServicesConfiguration } from '@/openapi-types';
import { defineComponent } from 'vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { useManagementServices } from '@/store/modules/management-services';
import { useNotifications } from '@/store/modules/notifications';
import { Permissions } from '@/global';
import { useUser } from '@/store/modules/user';
import { XrdEmptyPlaceholderRow } from '@niis/shared-ui';
import SelectSubsystemDialog from "@/components/systemSettings/managementServices/SelectSubsystemDialog.vue";
import SelectSecurityServerDialog from "@/components/systemSettings/managementServices/SelectSecurityServerDialog.vue";

export default defineComponent({
  components: {
    SelectSubsystemDialog,
    SelectSecurityServerDialog,
    XrdEmptyPlaceholderRow
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
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    fetchManagementServicesConfiguration(): void {
      this.loading = true;
      this.managementServicesStore
        .fetchManagementServicesConfiguration()
        .finally(() => (this.loading = false));
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
      return (
        this.managementServicesStore.managementServicesConfiguration
          .service_provider_id !== undefined
      );
    },
    isServiceProviderUnRegistered(): boolean {
      return (
        this.managementServicesStore.managementServicesConfiguration
          .security_server_id === ``
      );
    },
    openSelectSecurityServerDialog(): void {
      this.showSelectSecurityServerDialog = true;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

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

.action-cell {
  text-align: right;
  width: 100px;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}
</style>
