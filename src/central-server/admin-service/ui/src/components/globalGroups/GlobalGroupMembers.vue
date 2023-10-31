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
  <searchable-titled-view
    v-model="filter.query"
    @update:model-value="debouncedFetchItems"
  >
    <template #title>
      <i18n-t scope="global" keypath="globalGroup.groupMembers">
        <template #memberCount>
          <span data-test="member-count">{{ memberCount }}</span>
        </template>
      </i18n-t>
    </template>
    <template #append-search>
      <v-icon
        color="primary"
        class="filter-button"
        icon="mdi-filter-outline"
        @click="showFilterDialog = true"
      />
    </template>
    <template #header-buttons>
      <xrd-button
        v-if="allowAddAndRemoveGroupMembers"
        data-test="add-member-button"
        @click="showAddMemberDialog = true"
      >
        <v-icon
          class="xrd-large-button-icon"
          icon="mdi-plus-circle"
          size="x-large"
        />
        {{ $t('globalGroup.addMembers') }}
      </xrd-button>
    </template>
    <!-- Table - Members -->
    <v-data-table-server
      data-test="global-group-members"
      class="elevation-0 data-table"
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
        <div class="member-name xrd-clickable">
          <xrd-icon-base class="mr-4">
            <XrdIconFolderOutline />
          </xrd-icon-base>
          <div data-test="member-name">{{ item.name }}</div>
        </div>
      </template>

      <template #[`item.button`]="{ item }">
        <div class="cs-table-actions-wrap">
          <xrd-button
            v-if="allowAddAndRemoveGroupMembers"
            data-test="delete-member-button"
            text
            :outlined="false"
            @click="groupMemberToDelete = item"
            >{{ $t('action.remove') }}
          </xrd-button>
        </div>
      </template>

      <template #[`item.type`]="{ item }">
        <div>{{ item.client_id.type }}</div>
      </template>

      <template #[`item.instance`]="{ item }">
        <span data-test="instance">{{ item.client_id.instance_id }}</span>
      </template>

      <template #[`item.class`]="{ item }">
        <span data-test="class">{{ item.client_id.member_class }}</span>
      </template>

      <template #[`item.code`]="{ item }">
        <span data-test="code">{{ item.client_id.member_code }}</span>
      </template>

      <template #[`item.subsystem`]="{ item }">
        <span data-test="subsystem">{{
          item.client_id.subsystem_code
        }}</span>
      </template>

      <template #[`item.created_at`]="{ item }">
        <date-time :value="item.created_at" />
      </template>
    </v-data-table-server>

    <!-- Dialogs -->
    <group-members-filter-dialog
      v-if="showFilterDialog"
      :group-code="groupCode"
      cancel-button-text="action.cancel"
      @cancel="cancelFilter"
      @apply="applyFilter"
    />
    <add-group-members-dialog
      v-if="showAddMemberDialog"
      :group-code="groupCode"
      @add="refreshList"
      @cancel="showAddMemberDialog = false"
    />
    <delete-group-member-dialog
      v-if="groupMemberToDelete"
      ref="deleteDialog"
      :group-code="groupCode"
      :group-member="groupMemberToDelete"
      @delete="refreshList"
      @cancel="groupMemberToDelete = null"
    />
  </searchable-titled-view>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { Permissions } from '@/global';
import { DataTableHeader, PagingOptions } from '@/ui-types';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { mapActions, mapState, mapStores } from 'pinia';
import { GroupMemberListView, GroupMembersFilter } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import GroupMembersFilterDialog from './GroupMembersFilterDialog.vue';
import { debounce } from '@/util/helpers';
import AddGroupMembersDialog from './AddGroupMembersDialog.vue';
import DeleteGroupMemberDialog from './DeleteGroupMemberDialog.vue';
import SearchableTitledView from '@/components/ui/SearchableTitledView.vue';
import { VDataTableServer } from 'vuetify/labs/VDataTable';
import DateTime from '@/components/ui/DateTime.vue';
import { defaultItemsPerPageOptions } from '@/util/defaults';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    DateTime,
    SearchableTitledView,
    DeleteGroupMemberDialog,
    AddGroupMembersDialog,
    GroupMembersFilterDialog,
    VDataTableServer,
  },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      itemsPerPageOptions: defaultItemsPerPageOptions(),
      paging: { itemsPerPage: 10, page: 1 } as PagingOptions,
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
      return this.globalGroupStore.pagingOptions === undefined
        ? 0
        : this.globalGroupStore.pagingOptions.total_items;
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
        },
        {
          title: this.$t('globalGroup.class') as string,
          align: 'start',
          key: 'class',
        },
        {
          title: this.$t('globalGroup.code') as string,
          align: 'start',
          key: 'code',
        },
        {
          title: this.$t('globalGroup.subsystem') as string,
          align: 'start',
          key: 'subsystem',
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
    that = this;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.paging.page = 1;
      that.fetchItems();
    }, 600),
    changeOptions: async function (options: PagingOptions) {
      this.paging = options;
      await this.fetchItems();
    },
    fetchItems: async function () {
      this.loading = true;
      try {
        await this.globalGroupStore.findMembers(
          this.groupCode,
          this.paging,
          this.filter,
        );
      } catch (error: unknown) {
        this.showError(error);
      } finally {
        this.loading = false;
      }
    },
    cancelFilter(): void {
      this.showFilterDialog = false;
    },
    applyFilter(filter: GroupMembersFilter): void {
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

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

.member-name {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
}

.filter-button {
}
</style>
