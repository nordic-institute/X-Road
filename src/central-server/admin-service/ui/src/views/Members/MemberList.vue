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
    <div class="header-row">
      <div class="xrd-title-search">
        <div class="xrd-view-title">{{ $t('members.header') }}</div>
        <xrd-search v-model="search" />
      </div>
      <xrd-button
        v-if="hasPermissionToAddMember"
        data-test="add-member-button"
        @click="showAddMemberDialog = true"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <xrd-icon-add></xrd-icon-add>
        </xrd-icon-base>
        {{ $t('members.addMember') }}
      </xrd-button>
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="clientStore.clients"
      :search="search"
      :must-sort="true"
      :items-per-page="10"
      :options.sync="pagingSortingOptions"
      :server-items-length="clientStore.pagingOptions.total_items"
      class="elevation-0 data-table"
      item-key="client_id.encoded_id"
      :loader-height="2"
      :footer-props="{ itemsPerPageOptions: [10, 25] }"
      data-test="members-table"
      @update:options="changeOptions"
    >
      <template #[`item.member_name`]="{ item }">
        <div
          v-if="hasPermissionToMemberDetails"
          class="members-table-cell-name-action"
          @click="toDetails(item)"
        >
          <xrd-icon-base class="xrd-clickable mr-4"
            ><xrd-icon-folder-outline
          /></xrd-icon-base>

          {{ item.member_name }}
        </div>

        <div v-else class="members-table-cell-name">
          <xrd-icon-base class="mr-4"
            ><xrd-icon-folder-outline
          /></xrd-icon-base>

          {{ item.member_name }}
        </div>
      </template>
    </v-data-table>

    <!-- Dialogs -->
    <AddMemberDialog
      v-if="showAddMemberDialog"
      :show-dialog="showAddMemberDialog"
      @cancel="hideAddMemberDialog"
      @save="hideAddMemberDialogAndRefetch"
    >
    </AddMemberDialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';
import AddMemberDialog from '@/views/Members/Member/AddMemberDialog.vue';
import { DataTableHeader } from 'vuetify';
import { userStore } from '@/store/modules/user';
import { clientStore } from '@/store/modules/clients';
import { mapState } from 'pinia';
import { Permissions } from '@/global';
import { mapActions, mapStores } from 'pinia';
import { DataOptions } from 'vuetify';
import { debounce, toIdentifier } from '@/util/helpers';
import { notificationsStore } from '@/store/modules/notifications';
import { Client } from '@/openapi-types';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default Vue.extend({
  name: 'MemberList',
  components: {
    AddMemberDialog,
  },
  data() {
    return {
      search: '',
      loading: false,
      showOnlyPending: false,
      pagingSortingOptions: {} as DataOptions,
      showAddMemberDialog: false,
    };
  },
  computed: {
    ...mapStores(clientStore),
    ...mapState(userStore, ['hasPermission']),
    headers(): DataTableHeader[] {
      return [
        {
          text:
            (this.$t('global.memberName') as string) +
            ' (' +
            this.clientStore.clients?.length +
            ')',
          align: 'start',
          value: 'member_name',
          class: 'xrd-table-header members-table-header-name',
        },
        {
          text: this.$t('global.memberClass') as string,
          align: 'start',
          value: 'client_id.member_class',
          class: 'xrd-table-header members-table-header-class',
        },
        {
          text: this.$t('global.memberCode') as string,
          align: 'start',
          value: 'client_id.member_code',
          class: 'xrd-table-header members-table-header-code',
        },
      ];
    },
    hasPermissionToMemberDetails(): boolean {
      return this.hasPermission(Permissions.VIEW_MEMBER_DETAILS);
    },
    hasPermissionToAddMember(): boolean {
      return this.hasPermission(Permissions.ADD_NEW_MEMBER);
    },
  },
  watch: {
    search: function (newSearch, oldSearch) {
      this.debouncedFetchClients();
    },
  },
  created() {
    that = this;
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    hideAddMemberDialog(): void {
      this.showAddMemberDialog = false;
    },
    hideAddMemberDialogAndRefetch(): void {
      this.hideAddMemberDialog();
      this.fetchClients(this.pagingSortingOptions);
    },
    debouncedFetchClients: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.fetchClients(that.pagingSortingOptions);
    }, 600),
    toDetails(member: Client): void {
      this.$router.push({
        name: RouteName.MemberDetails,
        params: {
          memberid: toIdentifier(member.client_id),
          backTo: this.$router.currentRoute.path,
        },
      });
    },
    changeOptions: async function () {
      this.fetchClients(this.pagingSortingOptions);
    },
    fetchClients: async function (options: DataOptions) {
      this.loading = true;

      try {
        await this.clientStore.find(options, this.search);
      } catch (error: unknown) {
        this.showError(error);
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.members-table-cell-name-action {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

.members-table-cell-name {
  font-weight: 600;
  font-size: 14px;
}
</style>
