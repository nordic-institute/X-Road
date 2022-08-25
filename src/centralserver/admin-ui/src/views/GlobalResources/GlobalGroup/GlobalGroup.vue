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
  <div data-test="global-group-view">
    <div class="navigation-back" data-test="navigation-back">
      <router-link to="/settings/global-resources">
        <v-icon :color="colors.Purple100">mdi-chevron-left</v-icon>
        {{ $t('global.navigation.back') }}
      </router-link>
    </div>
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">{{ globalGroup.code }}</div>
      </div>
      <xrd-button v-if="allowGroupDelete" data-test="remove-group-button" outlined @click="showDeleteGroupDialog = true"
        ><v-icon class="xrd-large-button-icon">mdi-close-circle</v-icon>
        {{ $t('globalGroup.deleteGroup') }}</xrd-button
      >
    </div>

    <info-card
      :title-text="$t('globalGroup.description')"
      :info-text="globalGroup.description || ''"
      data-test="global-group-description"
      :action-text="$t('action.edit')"
      :show-action="allowDescriptionEdit"
      @actionClicked="showEditDescriptionDialog = true"
    />

    <!-- Toolbar buttons -->
    <div class="table-toolbar align-fix mt-8 pl-0">
      <div class="xrd-title-search align-fix mt-0 pt-0">
        <div class="xrd-view-title align-fix">
          {{ $t('globalGroup.groupMembers') }} ({{ memberCount }})
        </div>
        <xrd-search v-model="search" class="margin-fix" />
        <v-icon
          color="primary"
          class="ml-4 mt-1"
          @click="showFilterDialog = true"
          >mdi-filter-outline</v-icon
        >
      </div>
      <div class="only-pending mt-0">
        <xrd-button data-test="add-member-button" @click="showAddDialog = true"
          ><v-icon class="xrd-large-button-icon">mdi-plus-circle</v-icon>
          {{ $t('globalGroup.addMembers') }}</xrd-button
        >
      </div>
    </div>

    <!-- Table - Members -->
    <v-data-table
      :loading="loading"
      :headers="membersHeaders"
      :items="members"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.name`]="{ item }">
        <div class="member-name xrd-clickable" @click="toDetails(item)">
          <xrd-icon-base class="mr-4"><XrdIconFolderOutline /></xrd-icon-base>
          <div>{{ item.name }}</div>
        </div>
      </template>

      <template #[`item.button`]>
        <div class="cs-table-actions-wrap">
          <xrd-button text :outlined="false">{{
            $t('action.remove')
          }}</xrd-button>
        </div>
      </template>

      <template #footer>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Dialogs -->
    <FilterDialog
      :dialog="showFilterDialog"
      cancel-button-text="action.cancel"
      @cancel="showFilterDialog = false"
    ></FilterDialog>

    <!-- Edit Description Dialog -->
    <GlobalGroupEditDescriptionDialog
      v-if="showEditDescriptionDialog"
      :show-dialog="showEditDescriptionDialog"
      :group-code="globalGroup.code"
      :group-description="globalGroup.description"
      @edit="editDescription"
      @cancel="cancelEdit"
    >
    </GlobalGroupEditDescriptionDialog>

    <!-- Delete Group Dialog -->
    <GlobalGroupDeleteDialog
      v-if="showDeleteGroupDialog"
      :show-dialog="showDeleteGroupDialog"
      :group-code="globalGroup.code"
      @delete="deleteGlobalGroup"
      @cancel="cancelDelete"
    >
    </GlobalGroupDeleteDialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { Colors, Permissions, RouteName } from '@/global';
import { DataTableHeader } from 'vuetify';
import InfoCard from '@/components/ui/InfoCard.vue';
import FilterDialog from '@/views/GlobalResources/GlobalGroup/GroupMembersFilterDialog.vue';
import { useGlobalGroupsStore } from '@/store/modules/global-groups';
import { mapActions, mapState, mapStores } from 'pinia';
import { GlobalGroupResource, GroupMember } from '@/openapi-types';
import { notificationsStore } from '@/store/modules/notifications';
import { userStore } from '@/store/modules/user';
import GlobalGroupDeleteDialog from '@/views/GlobalResources/GlobalGroup/GlobalGroupDeleteDialog.vue';
import GlobalGroupEditDescriptionDialog from '@/views/GlobalResources/GlobalGroup/GlobalGroupEditDescriptionDialog.vue';

/**
 * Global group view
 */
export default Vue.extend({
  components: {
    GlobalGroupEditDescriptionDialog,
    GlobalGroupDeleteDialog,
    InfoCard,
    FilterDialog,
  },
  props: {
    groupId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,
      globalGroup: {} as GlobalGroupResource,
      members: [] as GroupMember[] | undefined,
      search: '',
      loading: false,
      permissions: Permissions,
      showAddDialog: false,
      showFilterDialog: false,
      showDeleteGroupDialog: false,
      showEditDescriptionDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroupsStore),
    ...mapState(userStore, ['hasPermission']),
    allowDescriptionEdit(): boolean {
      return this.hasPermission(Permissions.EDIT_GROUP_DESCRIPTION);
    },
    allowGroupDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_GROUP);
    },
    memberCount(): number {
      return this.members === undefined ? 0 : this.members.length;
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
  created() {
    this.loading = true;
    this.globalGroupStore.getById(this.groupId)
      .then((resp) => {
        this.globalGroup = resp;
        this.members = resp.members;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loading = false;
      });
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    goBack(): void {
      this.$router.go(-1);
    },
    cancelDelete(): void {
      this.showDeleteGroupDialog = false;
    },
    cancelEdit(): void {
      this.showEditDescriptionDialog = false;
    },
    deleteGlobalGroup(): void {
      this.globalGroupStore.deleteById(this.groupId)
        .then(() => {
          this.$router.replace({ name: RouteName.GlobalResources });
          this.showSuccess(this.$t('globalGroup.groupDeletedSuccessfully'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.showDeleteGroupDialog = false;
        });
    },
    editDescription(newDescription: string): void {
      this.globalGroupStore.editGroupDescription(this.groupId, { description: newDescription })
        .then((resp) => {
          this.globalGroup = resp.data;
          this.showSuccess(this.$t('globalGroup.descriptionSaved'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.showEditDescriptionDialog = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.navigation-back {
  color: $XRoad-Link;
  cursor: pointer;
  margin-bottom: 20px;

  a {
    text-decoration: none;
  }
}

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
