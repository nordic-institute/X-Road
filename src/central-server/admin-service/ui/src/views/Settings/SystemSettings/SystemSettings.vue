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
  <titled-view
    data-test="system-settings-view"
    title-key="tab.settings.systemSettings"
  >
    <!-- System Parameters -->
    <div
      id="system-parameters-anchor"
      class="mb-6"
      data-test="system-settings-system-parameters-card"
    >
      <v-card class="pb-4" flat>
        <div class="card-top">
          <div class="card-main-title">
            {{ $t('systemSettings.systemParameters') }}
          </div>
        </div>

        <table class="xrd-table mt-0 pb-3">
          <tbody>
            <tr>
              <td class="title-cell">
                <div>
                  <div>
                    {{ $t('systemSettings.instanceIdentifier') }}
                  </div>
                </div>
              </td>
              <td data-test="system-settings-instance-identifier-field">
                {{ instanceIdentifier }}
              </td>
              <td></td>
            </tr>
            <tr>
              <td>
                <div>
                  <div>
                    {{ $t('systemSettings.centralServerAddress') }}
                  </div>
                </div>
              </td>
              <td data-test="system-settings-central-server-address-field">
                {{ serverAddress }}
              </td>
              <td class="action-cell">
                <xrd-button
                  text
                  data-test="system-settings-central-server-address-edit-button"
                  @click="showEditServerAddressDialog = true"
                  >{{ $t('action.edit') }}
                </xrd-button>
              </td>
            </tr>
          </tbody>
        </table>
      </v-card>
    </div>
    <ManagementServices ref="managementServices" />
    <MemberClasses />
  </titled-view>
  <edit-server-address-dialog
    v-if="showEditServerAddressDialog"
    :service-address="serverAddress || ''"
    @cancel="showEditServerAddressDialog = false"
    @edit="refreshData"
  />
  <!-- Management Services -->
</template>

<script lang="ts">
import { useManagementServices } from '@/store/modules/management-services';
import { useSystem } from '@/store/modules/system';
import ManagementServices from '@/components/systemSettings/managementServices/ManagementServices.vue';
import MemberClasses from '@/components/systemSettings/MemberClasses.vue';
import EditServerAddressDialog from '@/components/systemSettings/EditServerAddressDialog.vue';
import { mapActions, mapState } from 'pinia';

/**
 * View for 'system settings' tab
 */
import { defineComponent } from 'vue';
import TitledView from '@/components/ui/TitledView.vue';

export default defineComponent({
  components: {
    TitledView,
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
      return this.getSystemStatus?.initialization_status
        ?.central_server_address;
    },
    instanceIdentifier(): string | undefined {
      return this.getSystemStatus?.initialization_status?.instance_identifier;
    },
  },
  created() {
    this.fetchSystemStatus();
  },
  methods: {
    ...mapActions(useSystem, [
      'fetchSystemStatus',
      'updateCentralServerAddress',
    ]),
    ...mapActions(useManagementServices, [
      'fetchManagementServicesConfiguration',
    ]),
    refreshData() {
      this.showEditServerAddressDialog = false;
      this.fetchSystemStatus();
      this.$refs.managementServices.fetchManagementServicesConfiguration();
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
