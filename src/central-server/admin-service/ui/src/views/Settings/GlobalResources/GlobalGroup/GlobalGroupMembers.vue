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
  <XrdCard class="bg-surface-container" variant="flat">
    <template #title>
      <span class="font-weight-medium title-component">
        <i18n-t scope="global" keypath="globalGroup.groupMembers">
          <template #memberCount>
            <span data-test="member-count">{{ memberCount }}</span>
          </template>
        </i18n-t>
      </span>
    </template>
    <template #append-title>
      <div class="d-flex flex-row align-center">
        <XrdSearchField
          v-model="query"
          data-test="search-query-field"
          width="360"
          :label="$t('action.search')"
          @update:model-value="debouncedFetchItems"
        />
        <v-btn class="xrd ml-4 mb-2" size="x-small" variant="outlined" icon="tune" color="primary" @click="showFilterDialog = true" />
      </div>
    </template>
    <template #title-actions>
      <XrdBtn
        v-if="allowAddAndRemoveGroupMembers"
        class="mr-4"
        variant="outlined"
        prepend-icon="add_circle"
        text="globalGroup.addMembers"
        data-test="add-member-button"
        @click="showAddMemberDialog = true"
      />
    </template>
    <v-data-table-server
      data-test="global-group-members"
      class="xrd"
      item-key="id"
      :loading="loading"
      :headers="membersHeaders"
      :items="globalGroupStore.members"
      :items-length="globalGroupStore.pagingOptions.total_items"
      :items-per-page-options="itemsPerPageOptions"
      :page="paging.page"
      :must-sort="true"
      :loader-height="2"
      @update:options="changeOptions"
    >
      <template #[`item.name`]="{ item }">
        <XrdLabelWithIcon icon="folder" semi-bold :label="item.name" />
      </template>

      <template #[`item.button`]="{ item }">
        <XrdBtn
          v-if="allowAddAndRemoveGroupMembers"
          data-test="delete-member-button"
          class="float-right"
          variant="text"
          text="action.remove"
          color="tertiary"
          @click="groupMemberToDelete = item"
        />
      </template>

      <template #[`item.type`]="{ item }">
        {{ item.client_id.type }}
      </template>

      <template #[`item.instance`]="{ item }">
        {{ item.client_id.instance_id }}
      </template>

      <template #[`item.class`]="{ item }">
        {{ item.client_id.member_class }}
      </template>

      <template #[`item.code`]="{ item }">
        {{ item.client_id.member_code }}
      </template>

      <template #[`item.subsystem`]="{ item }">
        {{ item.client_id.subsystem_code }}
      </template>

      <template #[`item.created_at`]="{ item }">
        <XrdDateTime :value="item.created_at" />
      </template>
      <template #bottom>
        <XrdPagination />
      </template>
    </v-data-table-server>
    <FilterGroupMembersDialog
      v-if="showFilterDialog"
      :group-code="groupCode"
      cancel-button-text="action.cancel"
      @cancel="cancelFilter"
      @apply="applyFilter"
    />
    <AddGroupMembersDialog v-if="showAddMemberDialog" :group-code="groupCode" @save="refreshList" @cancel="showAddMemberDialog = false" />
    <DeleteGroupMemberDialog
      v-if="groupMemberToDelete"
      ref="deleteDialog"
      :group-code="groupCode"
      :group-member="groupMemberToDelete"
      @delete="refreshList"
      @cancel="groupMemberToDelete = null"
    />
  </XrdCard>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { mapActions, mapState, mapStores } from 'pinia';

import { useNotifications, XrdBtn, XrdCard, XrdLabelWithIcon, XrdPagination, XrdDateTime } from '@niis/shared-ui';

import { Permissions } from '@/global';
import { GroupMemberListView, GroupMembersFilter } from '@/openapi-types';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { useUser } from '@/store/modules/user';
import { PagingOptions } from '@/ui-types';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import { debounce } from '@/util/helpers';

import AddGroupMembersDialog from './dialogs/AddGroupMembersDialog.vue';
import DeleteGroupMemberDialog from './dialogs/DeleteGroupMemberDialog.vue';
import FilterGroupMembersDialog from './dialogs/FilterGroupMembersDialog.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    XrdPagination,
    XrdCard,
    XrdDateTime,
    DeleteGroupMemberDialog,
    AddGroupMembersDialog,
    FilterGroupMembersDialog,
    XrdBtn,
    XrdLabelWithIcon,
  },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      itemsPerPageOptions: defaultItemsPerPageOptions(),
      paging: { itemsPerPage: 10, page: 1 } as PagingOptions,
      query: '',
      filter: { query: '' } as GroupMembersFilter,
      loading: false,
      showFilterDialog: false,
      groupMemberToDelete: null as GroupMemberListView | null,
      showAddMemberDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroups),
    ...mapState(useUser, ['hasPermission']),
    allowAddAndRemoveGroupMembers(): boolean {
      return this.hasPermission(Permissions.ADD_AND_REMOVE_GROUP_MEMBERS);
    },
    memberCount(): number {
      return this.globalGroupStore.pagingOptions === undefined ? 0 : this.globalGroupStore.pagingOptions.total_items;
    },
    membersHeaders(): DataTableHeader[] {
      return [
        {
          title: this.$t('globalGroup.memberName') as string,
          align: 'start',
          key: 'name',
        },
        {
          title: this.$t('globalGroup.type') as string,
          align: 'start',
          key: 'type',
        },
        {
          title: this.$t('globalGroup.instance') as string,
          align: 'start',
          key: 'instance',
          cellProps: { 'data-test': 'instance' },
        },
        {
          title: this.$t('globalGroup.class') as string,
          align: 'start',
          key: 'class',
          cellProps: { 'data-test': 'class' },
        },
        {
          title: this.$t('globalGroup.code') as string,
          align: 'start',
          key: 'code',
          cellProps: { 'data-test': 'code' },
        },
        {
          title: this.$t('globalGroup.subsystem') as string,
          align: 'start',
          key: 'subsystem',
          cellProps: { 'data-test': 'subsystem' },
        },
        {
          title: this.$t('globalGroup.added') as string,
          align: 'start',
          key: 'created_at',
        },
        {
          key: 'button',
          title: '',
          sortable: false,
        },
      ];
    },
  },
  created() {
    //eslint-disable-next-line @typescript-eslint/no-this-alias
    that = this;
  },
  methods: {
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.paging.page = 1;
      that.filter.query = that.query;
      that.fetchItems();
    }, 600),
    changeOptions: async function (options: PagingOptions) {
      this.paging = options;
      await this.fetchItems();
    },
    fetchItems: async function () {
      this.loading = true;
      try {
        await this.globalGroupStore.findMembers(this.groupCode, this.paging, this.filter);
      } catch (error: unknown) {
        this.addError(error);
      } finally {
        this.loading = false;
      }
    },
    cancelFilter(): void {
      this.showFilterDialog = false;
    },
    applyFilter(filter: GroupMembersFilter): void {
      this.query = '';
      this.filter = filter;
      this.fetchItems();
      this.showFilterDialog = false;
    },
    refreshList() {
      this.showAddMemberDialog = false;
      this.groupMemberToDelete = null;
      this.fetchItems();
    },
  },
});
</script>
