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
  <XrdView data-test="system-settings-view" title="tab.main.settings">
    <template #tabs>
      <SettingsViewTabs />
    </template>
    <XrdSubView>
      <XrdCard data-test="system-settings-system-parameters-card" class="mb-4" title="systemSettings.systemParameters">
        <XrdCardTable>
          <XrdCardTableRow
            data-test="system-settings-instance-identifier-field"
            label="systemSettings.instanceIdentifier"
            :value="instanceIdentifier"
          />
          <XrdCardTableRow
            data-test="system-settings-central-server-address-field"
            label="systemSettings.centralServerAddress"
            :value="serverAddress"
          >
            <XrdBtn
              data-test="system-settings-central-server-address-edit-button"
              variant="text"
              color="tertiary"
              text="action.edit"
              @click="showEditServerAddressDialog = true"
            />
          </XrdCardTableRow>
        </XrdCardTable>
      </XrdCard>
      <ManagementServices ref="managementServices" class="mb-4" />
      <MemberClasses />
    </XrdSubView>
    <EditServerAddressDialog
      v-if="showEditServerAddressDialog"
      :service-address="serverAddress || ''"
      @cancel="showEditServerAddressDialog = false"
      @save="refreshData"
    />
  </XrdView>
</template>

<script lang="ts">
/**
 * View for 'system settings' tab
 */
import { defineComponent } from 'vue';

import { mapActions, mapState } from 'pinia';

import { XrdBtn, XrdCard, XrdCardTable, XrdCardTableRow, XrdSubView, XrdView } from '@niis/shared-ui';

import { useManagementServices } from '@/store/modules/management-services';
import { useSystem } from '@/store/modules/system';

import EditServerAddressDialog from './EditServerAddressDialog.vue';
import ManagementServices from './managementServices/ManagementServices.vue';
import MemberClasses from './memberClass/MemberClasses.vue';
import SettingsViewTabs from '@/views/Settings/SettingsViewTabs.vue';

export default defineComponent({
  components: {
    XrdSubView,
    XrdView,
    XrdCard,
    XrdCardTable,
    XrdCardTableRow,
    XrdBtn,
    SettingsViewTabs,
    MemberClasses,
    ManagementServices,
    EditServerAddressDialog,
  },

  data() {
    return {
      search: '' as string,
      showOnlyPending: false,
      saveInProgress: false,
      showEditServerAddressDialog: false,
    };
  },
  computed: {
    ...mapState(useSystem, ['getSystemStatus']),
    serverAddress(): string | undefined {
      return this.getSystemStatus?.initialization_status?.central_server_address;
    },
    instanceIdentifier(): string | undefined {
      return this.getSystemStatus?.initialization_status?.instance_identifier;
    },
  },
  created() {
    this.fetchSystemStatus();
  },
  methods: {
    ...mapActions(useSystem, ['fetchSystemStatus', 'updateCentralServerAddress']),
    ...mapActions(useManagementServices, ['fetchManagementServicesConfiguration']),
    refreshData() {
      this.showEditServerAddressDialog = false;
      this.fetchSystemStatus();
      (
        this.$refs.managementServices as {
          fetchManagementServicesConfiguration: () => void;
        }
      ).fetchManagementServicesConfiguration();
    },
  },
});
</script>
