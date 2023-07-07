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
    class="mt-8"
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
      hide-default-footer
    >
      <template #[`item.issuer_common_name`]="{ item }">
        <div class="icon-cell" @click="navigateToCertificateDetails(item.id)">
          <xrd-icon-base icon-name="certificate" class="mr-4"
            ><XrdIconCertificate
          /></xrd-icon-base>
          {{ item.issuer_common_name }}
        </div>
      </template>
      <template #[`item.not_after`]="{ item }">
        {{ item.not_after | formatDateTime }}
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

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <DeleteAuthenticationCertificateDialog
      v-if="showDeleteConfirmationDialog"
      :authentication-certificate-id="authCertIdForDeletion"
      @cancel="cancelDeletion"
      @delete="finishDeletion"
    >
    </DeleteAuthenticationCertificateDialog>
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import DeleteAuthenticationCertificateDialog from '@/components/securityServers/DeleteAuthenticationCertificateDialog.vue';
import { Permissions, RouteName } from '@/global';
import { userStore } from '@/store/modules/user';
import { mapState, mapStores } from 'pinia';
import { SecurityServerAuthenticationCertificateDetails } from '@/openapi-types';
import { securityServerAuthCertStore } from '@/store/modules/security-servers-authentication-certificates';

export default Vue.extend({
  components: { DeleteAuthenticationCertificateDialog },
  data() {
    return {
      loading: false,
      showDeleteConfirmationDialog: false,
      authCertIdForDeletion: undefined as number | undefined,
    };
  },
  computed: {
    ...mapStores(securityServerAuthCertStore),
    ...mapState(userStore, ['hasPermission']),
    authenticationCertificates(): SecurityServerAuthenticationCertificateDetails[] {
      return this.securityServerAuthCertStore.authenticationCertificates;
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t(
            'securityServers.securityServer.certificationAuthority',
          ) as string,
          align: 'start',
          value: 'issuer_common_name',
          class: 'xrd-table-header',
        },
        {
          text: this.$t(
            'securityServers.securityServer.serialNumber',
          ) as string,
          align: 'start',
          value: 'serial',
          class: 'xrd-table-header',
        },
        {
          text: this.$t('securityServers.securityServer.subject') as string,
          align: 'start',
          value: 'subject_distinguished_name',
          class: 'xrd-table-header',
        },
        {
          text: this.$t('securityServers.securityServer.expires') as string,
          align: 'start',
          value: 'not_after',
          class: 'xrd-table-header',
        },
        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header',
        },
      ];
    },
  },
  created() {
    this.fetchSecurityServerAuthenticationCertificates();
  },
  methods: {
    fetchSecurityServerAuthenticationCertificates(): void {
      this.loading = true;
      this.securityServerAuthCertStore
        .fetch(this.$route.params.serverId)
        .finally(() => (this.loading = false));
    },
    hasDeletePermission(): boolean {
      return this.hasPermission(Permissions.DELETE_SECURITY_SERVER_AUTH_CERT);
    },
    openDeleteConfirmationDialog(authCertId: number): void {
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
    navigateToCertificateDetails(authCertId: number): void {
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
@import '~styles/tables';

.icon-cell {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
