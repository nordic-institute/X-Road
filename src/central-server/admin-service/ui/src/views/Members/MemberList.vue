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
    <searchable-titled-view
      v-model="search"
      title-key="members.header"
      @update:model-value="debouncedFetchClients"
    >
      <template #header-buttons>
        <xrd-button
          v-if="hasPermissionToAddMember"
          data-test="add-member-button"
          @click="showAddMemberDialog = true"
        >
          <xrd-icon-base class="xrd-large-button-icon">
            <xrd-icon-add />
          </xrd-icon-base>
          {{ $t('members.addMember') }}
        </xrd-button>
      </template>

      <v-data-table-server
        :loading="loading"
        :headers="headers"
        :items="clientStore.clients"
        :items-per-page="10"
        :items-per-page-options="itemsPerPageOptions"
        :items-length="clientStore.pagingOptions.total_items"
        class="xrd-table elevation-0 rounded"
        item-key="client_id.encoded_id"
        :loader-height="2"
        data-test="members-table"
        @update:options="changeOptions"
      >
        <template #top></template>
        <template #[`item.member_name`]="{ item, internalItem }">
          <div
            v-if="hasPermissionToMemberDetails"
            class="members-table-cell-name-action"
            @click="toDetails(item)"
          >
            <xrd-icon-base class="xrd-clickable mr-4">
              <xrd-icon-folder-outline />
            </xrd-icon-base>

            {{ internalItem.columns.member_name }}
          </div>

          <div v-else class="members-table-cell-name">
            <xrd-icon-base class="mr-4">
              <xrd-icon-folder-outline />
            </xrd-icon-base>

            {{ internalItem.columns.member_name }}
          </div>
        </template>
      </v-data-table-server>
    </searchable-titled-view>
    <add-member-dialog
      v-if="showAddMemberDialog"
      @cancel="hideAddMemberDialog"
      @save="hideAddMemberDialogAndRefetch"
    >
    </add-member-dialog>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions, RouteName } from '@/global';
import AddMemberDialog from '@/views/Members/Member/AddMemberDialog.vue';
import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/clients';
import { mapActions, mapState, mapStores } from 'pinia';
import { debounce, toIdentifier } from '@/util/helpers';
import { useNotifications } from '@/store/modules/notifications';
import { Client } from '@/openapi-types';
import { VDataTableServer } from 'vuetify/labs/VDataTable';
import { DataQuery, DataTableHeader } from '@/ui-types';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import SearchableTitledView from '@/components/ui/SearchableTitledView.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    SearchableTitledView,
    AddMemberDialog,
    VDataTableServer,
  },
  data() {
    return {
      dataQuery: { page: 1, itemsPerPage: 10 } as DataQuery,
      itemsPerPageOptions: defaultItemsPerPageOptions(),
      search: '',
      loading: false,
      showOnlyPending: false,
      showAddMemberDialog: false,
    };
  },
  computed: {
    ...mapStores(useClient),
    ...mapState(useUser, ['hasPermission']),
    headers(): DataTableHeader[] {
      return [
        {
          title: `${this.$t('global.memberName')} (${this.clientStore.clients
            ?.length})`,
          align: 'start',
          key: 'member_name',
        },
        {
          title: this.$t('global.memberClass') as string,
          align: 'start',
          key: 'client_id.member_class',
        },
        {
          title: this.$t('global.memberCode') as string,
          align: 'start',
          key: 'client_id.member_code',
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
  created() {
    that = this;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    hideAddMemberDialog(): void {
      this.showAddMemberDialog = false;
    },
    hideAddMemberDialogAndRefetch(): void {
      this.hideAddMemberDialog();
      this.fetchClients();
    },
    debouncedFetchClients: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.fetchClients();
    }, 600),
    toDetails(member: Client): void {
      this.$router.push({
        name: RouteName.MemberDetails,
        params: {
          memberid: toIdentifier(member.client_id),
        },
      });
    },
    changeOptions: async function ({ itemsPerPage, page, sortBy }) {
      this.dataQuery.itemsPerPage = itemsPerPage;
      this.dataQuery.page = page;
      this.dataQuery.sortBy = sortBy[0]?.key;
      this.dataQuery.sortOrder = sortBy[0]?.order;
      this.fetchClients();
    },
    fetchClients: async function () {
      this.loading = true;
      this.dataQuery.search = this.search;
      try {
        await this.clientStore.find(this.dataQuery);
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
@import '@/assets/colors';
@import '@/assets/tables';

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
