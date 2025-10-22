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
  <XrdView title="tab.main.settings">
    <template #tabs>
      <SettingsViewTabs />
    </template>
    <XrdSubView>
      <template #header>
        <v-spacer />
        <XrdBtn
          v-if="allowAddGlobalGroup"
          data-test="add-global-group-button"
          text="globalResources.addGlobalGroup"
          prepend-icon="add_circle"
          @click="showAddGroupDialog = true"
        />
      </template>
      <v-data-table
        data-test="global-groups-table"
        item-key="id"
        class="xrd"
        hide-default-footer
        :loading="groupsLoading"
        :headers="globalGroupsHeaders"
        :items="globalGroups"
        :must-sort="true"
        :items-per-page="-1"
        :loader-height="2"
      >
        <template #[`item.code`]="{ item }">
          <XrdLabelWithIcon
            data-test="group-code"
            icon="group"
            semi-bold
            clickable
            :label="item.code"
            @navigate="toDetails(item)"
          />
        </template>
        <template #[`item.updated_at`]="{ item }">
          <XrdDateTime :value="item.updated_at" />
        </template>
      </v-data-table>
      <add-group-dialog
        v-if="showAddGroupDialog"
        @cancel="closeAddGroupDialog()"
        @save="groupAdded()"
      />
    </XrdSubView>
  </XrdView>
</template>
<script lang="ts">
import { defineComponent } from 'vue';

import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { mapState, mapStores } from 'pinia';

import {
  useNotifications,
  XrdBtn,
  XrdDateTime,
  XrdLabelWithIcon,
  XrdSubView,
  XrdView,
} from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { GlobalGroupResource } from '@/openapi-types';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { useUser } from '@/store/modules/user';

import SettingsViewTabs from '../SettingsViewTabs.vue';
import AddGroupDialog from './AddGroupDialog.vue';

export default defineComponent({
  components: {
    XrdDateTime,
    AddGroupDialog,
    XrdSubView,
    XrdBtn,
    XrdView,
    XrdLabelWithIcon,
    SettingsViewTabs,
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      showAddGroupDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroups),
    ...mapState(useUser, ['hasPermission']),
    globalGroups(): GlobalGroupResource[] {
      return this.globalGroupStore.globalGroups;
    },
    groupsLoading(): boolean {
      return this.globalGroupStore.groupsLoading;
    },
    allowAddGlobalGroup(): boolean {
      return this.hasPermission(Permissions.ADD_GLOBAL_GROUP);
    },
    globalGroupsHeaders(): DataTableHeader[] {
      return [
        {
          title: this.$t('globalResources.code') as string,
          align: 'start',
          key: 'code',
        },
        {
          title: this.$t('globalResources.description') as string,
          align: 'start',
          key: 'description',
        },
        {
          title: this.$t('globalResources.memberCount') as string,
          align: 'start',
          key: 'member_count',
        },
        {
          title: this.$t('globalResources.updated') as string,
          align: 'start',
          key: 'updated_at',
        },
      ];
    },
  },
  created() {
    this.fetchAllGroups();
  },
  methods: {
    closeAddGroupDialog(): void {
      this.showAddGroupDialog = false;
    },
    groupAdded(): void {
      this.showAddGroupDialog = false;
    },
    toDetails(globalGroup: GlobalGroupResource): void {
      this.$router.push({
        name: RouteName.GlobalGroup,
        params: { groupCode: globalGroup.code || '' },
      });
    },
    fetchAllGroups(): void {
      this.globalGroupStore.findAll().catch((error) => this.addError(error));
    },
  },
});
</script>
