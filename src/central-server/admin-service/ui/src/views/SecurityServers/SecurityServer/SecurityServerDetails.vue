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
  <div>
    <xrd-empty-placeholder
      :data="securityServer"
      :loading="loading"
      :no-items-text="$t('noData.noData')"
      skeleton-type="table-heading"
    />
    <main
      v-if="securityServer && !loading"
      data-test="security-server-details-view"
      class="mt-8"
    >
      <!-- Security Server Details -->
      <div id="security-server-details">
        <info-card
          :title-text="$t('securityServers.ownerName')"
          :info-text="securityServer.owner_name"
          data-test="security-server-owner-name"
        />

        <info-card
          :title-text="$t('securityServers.ownerClass')"
          :info-text="securityServer.server_id.member_class"
          data-test="security-server-owner-class"
        />

        <info-card
          :title-text="$t('securityServers.ownerCode')"
          :info-text="securityServer.server_id.member_code"
          data-test="security-server-owner-code"
        />
      </div>

      <info-card
        class="mb-6"
        :title-text="$t('securityServers.serverCode')"
        :info-text="securityServer.server_id.server_code"
        data-test="security-server-server-code"
      />

      <info-card
        class="mb-6"
        data-test="security-server-address"
        :title-text="$t('securityServers.address')"
        :info-text="securityServer.server_address"
        :action-text="$t('action.edit')"
        :show-action="canEditAddress"
        @action-clicked="showEditAddressDialog = true"
      />

      <info-card
        :title-text="$t('securityServers.registered')"
        data-test="security-server-registered"
        ><date-time :value="securityServer.created_at" with-seconds
      /></info-card>

      <div class="delete-action" @click="showDeleteServerDialog = true">
        <div>
          <v-icon
            class="xrd-large-button-icon"
            :color="colors.Purple100"
            icon="mdi-close-circle"
          />
        </div>
        <div
          v-if="canDeleteServer"
          class="action-text"
          data-test="btn-delete-security-server"
        >
          {{
            `${$t('securityServers.securityServer.deleteSecurityServer')} "${
              securityServer.server_id.server_code
            }"`
          }}
        </div>
      </div>
    </main>
    <delete-security-server-dialog
      v-if="serverCode && showDeleteServerDialog"
      :server-code="serverCode"
      :security-server-id="serverId"
      @cancel="showDeleteServerDialog = false"
    />
    <edit-security-server-address-dialog
      v-if="address && showEditAddressDialog"
      :address="address"
      :security-server-id="serverId"
      @cancel="showEditAddressDialog = false"
      @address-updated="showEditAddressDialog = false"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';
import { Colors, Permissions, RouteName } from '@/global';
import { mapActions, mapState, mapStores } from 'pinia';
import { useSecurityServer } from '@/store/modules/security-servers';
import { SecurityServer } from '@/openapi-types';
import { useUser } from '@/store/modules/user';
import EditSecurityServerAddressDialog from '@/views/SecurityServers/SecurityServer/EditSecurityServerAddressDialog.vue';
import DeleteSecurityServerDialog from '@/views/SecurityServers/SecurityServer/DeleteSecurityServerDialog.vue';
import { useNotifications } from '@/store/modules/notifications';
import DateTime from '@/components/ui/DateTime.vue';

/**
 * Component for a Security server details view
 */
export default defineComponent({
  name: 'SecurityServerDetails',
  components: {
    DateTime,
    DeleteSecurityServerDialog,
    EditSecurityServerAddressDialog,
    InfoCard,
  },
  props: {
    serverId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,
      showEditAddressDialog: false,
      showDeleteServerDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapStores(useSecurityServer),
    securityServer(): SecurityServer | null {
      return this.securityServerStore.currentSecurityServer;
    },
    loading(): boolean {
      return this.securityServerStore.currentSecurityServerLoading;
    },
    canEditAddress(): boolean {
      return this.hasPermission(Permissions.EDIT_SECURITY_SERVER_ADDRESS);
    },
    canDeleteServer(): boolean {
      return this.hasPermission(Permissions.DELETE_SECURITY_SERVER);
    },
    address(): string | null {
      return this.securityServer?.server_address || null;
    },
    serverCode(): string | null {
      return this.securityServer?.server_id.server_code || null;
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showSuccess']),
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

#security-server-details {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  .details-card {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }

    &:last-child {
      margin-left: 30px;
    }
  }
}

#global-groups-table {
  tbody tr td:last-child {
    width: 50px;
  }
}

.delete-action {
  margin-top: 34px;
  color: $XRoad-Link;
  cursor: pointer;
  display: flex;
  flex-direction: row;

  .action-text {
    margin-top: 2px;
  }
}
</style>
