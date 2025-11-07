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
  <XrdView title="members.header">
    <template #append-header>
      <div class="ml-6">
        <v-text-field
          v-model="search"
          data-test="search-query-field"
          class="xrd"
          width="320"
          prepend-inner-icon="search"
          single-line
          :label="$t('action.search')"
          @update:model-value="debouncedFetchClients"
        />
      </div>
      <XrdBtn
        v-if="hasPermissionToAddMember"
        data-test="add-member-button"
        class="ml-auto"
        prepend-icon="create_new_folder"
        text="members.addMember"
        @click="showAddMemberDialog = true"
      />
    </template>
    <v-data-table-server
      data-test="members-table"
      class="xrd bg-surface-container xrd-rounded-16"
      item-key="client_id.encoded_id"
      :page="page"
      :loading="loading"
      :headers="headers"
      :header-props="{ class: 'font-weight-medium body-regular' }"
      :items="clientStore.clients"
      :items-length="clientStore.pagingOptions.total_items"
      :loader-height="2"
      @update:options="changeOptions"
    >
      <template #top></template>
      <template #[`item.member_name`]="{ item, internalItem }">
        <XrdLabelWithIcon
          data-test="member-name"
          icon="folder"
          :label="internalItem.columns.member_name"
          :clickable="hasPermissionToMemberDetails"
          semi-bold
          @navigate="toDetails(item)"
        />
      </template>
      <template #bottom>
        <XrdPagination />
      </template>
    </v-data-table-server>
    <add-member-dialog
      v-if="showAddMemberDialog"
      @cancel="hideAddMemberDialog"
      @save="hideAddMemberDialogAndRefetch"
    />
  </XrdView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { mapState, mapStores } from 'pinia';

import {
  useNotifications,
  XrdBtn,
  XrdLabelWithIcon,
  XrdPagination,
  XrdView,
} from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { Client } from '@/openapi-types';
import { useClient } from '@/store/modules/clients';
import { useUser } from '@/store/modules/user';
import { DataQuery, PagingOptions } from '@/ui-types';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import { debounce, toIdentifier } from '@/util/helpers';

import AddMemberDialog from '@/views/Members/Member/AddMemberDialog.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    XrdView,
    AddMemberDialog,
    XrdBtn,
    XrdPagination,
    XrdLabelWithIcon,
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      page: 1,
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
          title: `${this.$t('global.memberName')} (${
            this.clientStore.clients?.length
          })`,
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
    //eslint-disable-next-line @typescript-eslint/no-this-alias
    that = this;
  },
  methods: {
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
          memberId: toIdentifier(member.client_id),
        },
      });
    },
    changeOptions: async function ({
      itemsPerPage,
      page,
      sortBy,
    }: PagingOptions) {
      this.dataQuery.itemsPerPage = itemsPerPage;
      this.dataQuery.page = page;
      this.dataQuery.sortBy = sortBy[0]?.key;
      const order = sortBy[0]?.order;
      this.dataQuery.sortOrder =
        order === undefined
          ? undefined
          : order === true || order === 'asc'
            ? 'asc'
            : 'desc';
      this.fetchClients();
    },
    fetchClients: async function () {
      this.loading = true;
      this.dataQuery.search = this.search;
      try {
        await this.clientStore.find(this.dataQuery);
      } catch (error: unknown) {
        this.addError(error);
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>
