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
    <XrdEmptyPlaceholder
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
        @actionClicked="$refs.editAddressDialog.open()"
      />

      <info-card
        :title-text="$t('securityServers.registered')"
        :info-text="securityServer.created_at | formatDateTimeSeconds"
        data-test="security-server-registered"
      />

      <div class="delete-action" @click="$refs.deleteDialog.open()">
        <div>
          <v-icon class="xrd-large-button-icon" :color="colors.Purple100"
            >mdi-close-circle
          </v-icon>
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
    <delete-security-server-address-dialog
      ref="deleteDialog"
      :server-code="serverCode"
      :security-server-id="serverId"
      @deleted="deleteServer"
    />
    <edit-security-server-address-dialog
      ref="editAddressDialog"
      :address="address"
      :security-server-id="serverId"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';
import { Colors, Permissions, RouteName } from '@/global';
import { mapActions, mapState, mapStores } from 'pinia';
import { useSecurityServerStore } from '@/store/modules/security-servers';
import { SecurityServer } from '@/openapi-types';
import { userStore } from '@/store/modules/user';
import EditSecurityServerAddressDialog from '@/views/SecurityServers/SecurityServer/EditSecurityServerAddressDialog.vue';
import DeleteSecurityServerAddressDialog from '@/views/SecurityServers/SecurityServer/DeleteSecurityServerAddressDialog.vue';
import { notificationsStore } from '@/store/modules/notifications';

/**
 * Component for a Security server details view
 */
export default Vue.extend({
  name: 'SecurityServerDetails',
  components: {
    DeleteSecurityServerAddressDialog,
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
    };
  },
  computed: {
    ...mapState(userStore, ['hasPermission']),
    ...mapStores(useSecurityServerStore),
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
    ...mapActions(notificationsStore, ['showSuccess']),
    deleteServer() {
      this.$router.replace({
        name: RouteName.SecurityServers,
      });
      this.showSuccess(
        this.$t('securityServers.dialogs.deleteAddress.success'),
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

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
