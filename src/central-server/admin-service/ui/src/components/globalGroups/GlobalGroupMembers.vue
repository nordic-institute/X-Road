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
  <section>
    <header class="table-toolbar align-fix mt-8 pl-0">
      <div class="xrd-title-search align-fix mt-0 pt-0">
        <div class="xrd-view-title align-fix">
          <i18n path="globalGroup.groupMembers">
            <template #memberCount>
              <span data-test="member-count">{{ memberCount }}</span>
            </template>
          </i18n>
        </div>
        <xrd-search v-model="filter.query" class="margin-fix" />
        <v-icon
          color="primary"
          class="ml-4 mt-1"
          @click="showFilterDialog = true"
          >mdi-filter-outline
        </v-icon>
      </div>
      <div class="only-pending mt-0">
        <xrd-button
          v-if="allowAddAndRemoveGroupMembers"
          data-test="add-member-button"
          @click="$refs.addDialog.open()"
        >
          <v-icon class="xrd-large-button-icon">mdi-plus-circle</v-icon>
          {{ $t('globalGroup.addMembers') }}
        </xrd-button>
      </div>
    </header>

    <!-- Table - Members -->
    <v-data-table
      data-test="global-group-members"
      class="elevation-0 data-table"
      item-key="id"
      :loading="loading"
      :headers="membersHeaders"
      :items="globalGroupStore.members"
      :search="filter.query"
      :must-sort="true"
      :options.sync="pagingSortingOptions"
      :server-items-length="globalGroupStore.pagingOptions.total_items"
      :loader-height="2"
      :footer-props="{ itemsPerPageOptions: [10, 25, 50] }"
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
            @click="$refs.deleteDialog.open(item)"
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
        <span data-test="subsystem">{{ item.client_id.subsystem_code }}</span>
      </template>

      <template #[`item.created_at`]="{ item }">
        <div>{{ item.created_at | formatDateTime }}</div>
      </template>
    </v-data-table>

    <!-- Dialogs -->
    <group-members-filter-dialog
      :group-code="groupCode"
      :dialog="showFilterDialog"
      cancel-button-text="action.cancel"
      @cancel="cancelFilter"
      @apply="applyFilter"
    />
    <add-group-members-dialog
      ref="addDialog"
      :group-code="groupCode"
      @added="refreshList"
    />
    <delete-group-member-dialog
      ref="deleteDialog"
      :group-code="groupCode"
      @deleted="refreshList"
    />
  </section>
</template>

<script lang="ts">
import Vue from 'vue';

import { Permissions } from '@/global';
import { DataOptions, DataTableHeader } from 'vuetify';
import { useGlobalGroupsStore } from '@/store/modules/global-groups';
import { mapActions, mapState, mapStores } from 'pinia';
import { GroupMembersFilter } from '@/openapi-types';
import { notificationsStore } from '@/store/modules/notifications';
import { userStore } from '@/store/modules/user';
import GroupMembersFilterDialog from './GroupMembersFilterDialog.vue';
import { debounce } from '@/util/helpers';
import AddGroupMembersDialog from './AddGroupMembersDialog.vue';
import DeleteGroupMemberDialog from './DeleteGroupMemberDialog.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default Vue.extend({
  components: {
    DeleteGroupMemberDialog,
    AddGroupMembersDialog,
    GroupMembersFilterDialog,
  },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      pagingSortingOptions: {} as DataOptions,
      filter: {} as GroupMembersFilter,
      loading: false,
      showFilterDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroupsStore),
    ...mapState(userStore, ['hasPermission']),
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
          text: this.$t('globalGroup.memberName') as string,
          align: 'start',
          value: 'name',
          class: 'xrd-table-header gp-table-header-member-name',
        },
        {
          text: this.$t('globalGroup.type') as string,
          align: 'start',
          value: 'type',
          class: 'xrd-table-header gp-table-header-member-type',
        },
        {
          text: this.$t('globalGroup.instance') as string,
          align: 'start',
          value: 'instance',
          class: 'xrd-table-header gp-table-header-member-instance',
        },
        {
          text: this.$t('globalGroup.class') as string,
          align: 'start',
          value: 'class',
          class: 'xrd-table-header gp-table-header-member-class',
        },
        {
          text: this.$t('globalGroup.code') as string,
          align: 'start',
          value: 'code',
          class: 'xrd-table-header gp-table-header-member-code',
        },
        {
          text: this.$t('globalGroup.subsystem') as string,
          align: 'start',
          value: 'subsystem',
          class: 'xrd-table-header gp-table-header-member-subsystem',
        },
        {
          text: this.$t('globalGroup.added') as string,
          align: 'start',
          value: 'created_at',
          class: 'xrd-table-header gp-table-header-member-created',
        },
        {
          value: 'button',
          text: '',
          sortable: false,
          class: 'xrd-table-header groups-table-header-buttons',
        },
      ];
    },
  },
  watch: {
    filter: {
      handler() {
        this.pagingSortingOptions.page = 1;
        this.debouncedFetchItems();
      },
      deep: true,
    },
  },
  created() {
    that = this;
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.fetchItems(that.pagingSortingOptions, that.filter);
    }, 600),
    changeOptions: async function () {
      await this.fetchItems(this.pagingSortingOptions, this.filter);
    },
    fetchItems: async function (
      options: DataOptions,
      filter: GroupMembersFilter,
    ) {
      this.loading = true;
      try {
        await this.globalGroupStore.findMembers(
          this.groupCode,
          options,
          filter,
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
      this.filter.query = '';
      this.fetchItems(this.pagingSortingOptions, filter);
      this.showFilterDialog = false;
    },
    refreshList() {
      this.fetchItems(this.pagingSortingOptions, this.filter);
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.member-name {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.only-pending {
  display: flex;
  justify-content: flex-end;
}
</style>
