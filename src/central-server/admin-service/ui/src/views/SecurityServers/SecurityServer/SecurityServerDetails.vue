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
  <XrdSubView>
    <XrdCard data-test="security-server-details-view" :loading="loading">
      <XrdCardTable>
        <XrdCardTableRow
          data-test="security-server-owner-name"
          label="securityServers.ownerName"
          :value="securityServer?.owner_name"
        />

        <XrdCardTableRow
          data-test="security-server-owner-class"
          label="securityServers.ownerClass"
          :value="securityServer?.server_id.member_class"
        />

        <XrdCardTableRow
          data-test="security-server-owner-code"
          label="securityServers.ownerCode"
          :value="securityServer?.server_id.member_code"
        />

        <XrdCardTableRow
          data-test="security-server-server-code"
          label="securityServers.serverCode"
          :value="securityServer?.server_id.server_code"
        />

        <XrdCardTableRow
          data-test="security-server-address"
          label="securityServers.address"
          :value="securityServer?.server_address"
        >
          <XrdBtn
            v-if="canEditAddress"
            variant="text"
            color="tertiary"
            text="action.edit"
            @click="showEditAddressDialog = true"
          />
        </XrdCardTableRow>

        <XrdCardTableRow
          data-test="security-server-maintenance-mode"
          label="securityServers.maintenanceMode"
        >
          <template v-if="securityServer?.in_maintenance_mode" #value>
            <v-icon class="mr-2" icon="check_circle filled" color="success" />
            {{ securityServer.maintenance_mode_message }}
          </template>
        </XrdCardTableRow>

        <XrdCardTableRow
          data-test="security-server-registered"
          label="securityServers.registered"
        >
          <template #value>
            <XrdDateTime :value="securityServer?.created_at" with-seconds />
          </template>
        </XrdCardTableRow>
      </XrdCardTable>
    </XrdCard>
    <edit-security-server-address-dialog
      v-if="showEditAddressDialog"
      :address="address"
      :security-server-id="serverId"
      @cancel="closeEditAddress"
      @save="closeEditAddress"
    />
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapState, mapStores } from 'pinia';

import {
  XrdBtn,
  XrdCard,
  XrdCardTable,
  XrdCardTableRow,
  XrdDateTime,
  XrdSubView,
} from '@niis/shared-ui';

import { Permissions } from '@/global';
import { SecurityServer } from '@/openapi-types';
import { useSecurityServer } from '@/store/modules/security-servers';
import { useUser } from '@/store/modules/user';

import EditSecurityServerAddressDialog from './dialogs/EditSecurityServerAddressDialog.vue';

/**
 * Component for a Security server details view
 */
export default defineComponent({
  name: 'SecurityServerDetails',
  components: {
    XrdCardTable,
    XrdCardTableRow,
    XrdBtn,
    XrdCard,
    XrdSubView,
    XrdDateTime,
    EditSecurityServerAddressDialog,
  },
  props: {
    serverId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      showEditAddressDialog: false,
      showDeleteServerDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapStores(useSecurityServer),
    securityServer(): SecurityServer | undefined {
      return this.securityServerStore.current;
    },
    loading(): boolean {
      return this.securityServerStore.loadingCurrent || false;
    },
    canEditAddress(): boolean {
      return this.hasPermission(Permissions.EDIT_SECURITY_SERVER_ADDRESS);
    },
    address(): string {
      return this.securityServer?.server_address ?? '';
    },
    serverCode(): string | null {
      return this.securityServer?.server_id.server_code || null;
    },
  },
  methods: {
    closeEditAddress() {
      this.showEditAddressDialog = false;
    },
  },
});
</script>
