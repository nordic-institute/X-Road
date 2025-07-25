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
  <main
    data-test="security-server-authentication-certificates-view"
    class="mt-5"
  >
    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="authenticationCertificates"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
    >
      <template #[`item.issuer_common_name`]="{ item }">
        <div class="icon-cell" @click="navigateToCertificateDetails(item.id)">
          <xrd-icon-base icon-name="certificate" class="mr-4">
            <xrd-icon-certificate />
          </xrd-icon-base>
          {{ item.issuer_common_name }}
        </div>
      </template>
      <template #[`item.not_after`]="{ item }">
        <date-time :value="item.not_after" />
      </template>

      <template #[`item.button`]="{ item }">
        <div class="cs-table-actions-wrap">
          <xrd-button
            v-if="hasDeletePermission"
            data-test="delete-AC-button"
            text
            :outlined="false"
            @click="openDeleteConfirmationDialog(item.id)"
          >
            {{ $t('action.delete') }}
          </xrd-button>
        </div>
      </template>

      <template #bottom>
        <XrdDataTableFooter />
      </template>
    </v-data-table>

    <delete-authentication-certificate-dialog
      v-if="
        securityServerId &&
        authCertIdForDeletion &&
        showDeleteConfirmationDialog
      "
      :authentication-certificate-id="authCertIdForDeletion.toString()"
      :security-server-id="securityServerId"
      @cancel="cancelDeletion"
      @delete="finishDeletion"
    >
    </delete-authentication-certificate-dialog>
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import DeleteAuthenticationCertificateDialog from '@/components/securityServers/DeleteAuthenticationCertificateDialog.vue';
import { Permissions, RouteName } from '@/global';
import { useUser } from '@/store/modules/user';
import { mapState, mapStores } from 'pinia';
import {
  SecurityServerAuthenticationCertificateDetails,
  SecurityServerId,
} from '@/openapi-types';
import { useSecurityServerAuthCert } from '@/store/modules/security-servers-authentication-certificates';
import { useSecurityServer } from '@/store/modules/security-servers';
import DateTime from '@/components/ui/DateTime.vue';
import { XrdIconCertificate, XrdDataTableFooter } from '@niis/shared-ui';
import { DataTableHeader } from '@/ui-types';

export default defineComponent({
  components: {
    XrdDataTableFooter,
    DateTime,
    DeleteAuthenticationCertificateDialog,
    XrdIconCertificate,
  },
  props: {
    serverId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      showDeleteConfirmationDialog: false,
      authCertIdForDeletion: undefined as number | undefined,
    };
  },
  computed: {
    ...mapStores(useSecurityServerAuthCert),
    ...mapStores(useSecurityServer),
    ...mapState(useUser, ['hasPermission']),
    authenticationCertificates(): SecurityServerAuthenticationCertificateDetails[] {
      return this.securityServerAuthCertStore.authenticationCertificates;
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t(
            'securityServers.securityServer.certificationAuthority',
          ) as string,
          align: 'start',
          key: 'issuer_common_name',
        },
        {
          title: this.$t(
            'securityServers.securityServer.serialNumber',
          ) as string,
          align: 'start',
          key: 'serial',
        },
        {
          title: this.$t('securityServers.securityServer.subject') as string,
          align: 'start',
          key: 'subject_distinguished_name',
        },
        {
          title: this.$t('securityServers.securityServer.expires') as string,
          align: 'start',
          key: 'not_after',
        },
        {
          title: '',
          key: 'button',
          sortable: false,
        },
      ];
    },
    securityServerId(): SecurityServerId | undefined {
      return this.securityServerStore.currentSecurityServer?.server_id;
    },
  },
  created() {
    this.fetchSecurityServerAuthenticationCertificates();
  },
  methods: {
    fetchSecurityServerAuthenticationCertificates(): void {
      this.loading = true;
      this.securityServerAuthCertStore
        .fetch(this.serverId)
        .finally(() => (this.loading = false));
    },
    hasDeletePermission(): boolean {
      return this.hasPermission(Permissions.DELETE_SECURITY_SERVER_AUTH_CERT);
    },
    openDeleteConfirmationDialog(authCertId?: number): void {
      this.authCertIdForDeletion = authCertId;
      this.showDeleteConfirmationDialog = true;
    },
    cancelDeletion(): void {
      this.showDeleteConfirmationDialog = false;
    },
    finishDeletion(): void {
      this.showDeleteConfirmationDialog = false;
      this.fetchSecurityServerAuthenticationCertificates();
    },
    navigateToCertificateDetails(authCertId?: number): void {
      if (!authCertId) {
        return;
      }
      this.$router.push({
        name: RouteName.SecurityServerAuthenticationCertificate,
        params: {
          authenticationCertificateId: authCertId.toString(),
        },
      });
    },
  },
});
</script>
<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/tables' as *;
@use '@niis/shared-ui/src/assets/colors';

.icon-cell {
  color: colors.$Purple100;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}
</style>
