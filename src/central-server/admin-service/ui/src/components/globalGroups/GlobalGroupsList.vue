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
  <titled-view title-key="globalResources.globalGroups">
    <template #header-buttons>
      <xrd-button
        v-if="allowAddGlobalGroup"
        data-test="add-global-group-button"
        @click="showAddGroupDialog = true"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <xrd-icon-add />
        </xrd-icon-base>
        {{ $t('globalResources.addGlobalGroup') }}
      </xrd-button>
    </template>

    <!-- Table 1 - Global Groups -->
    <v-data-table
      :loading="groupsLoading"
      :headers="globalGroupsHeaders"
      :items="globalGroups"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
      data-test="global-groups-table"
    >
      <template #[`item.code`]="{ item }">
        <div class="group-code xrd-clickable" @click="toDetails(item)">
          <xrd-icon-base class="mr-4">
            <XrdIconFolder />
          </xrd-icon-base>
          <div data-test="group-code">{{ item.code }}</div>
        </div>
      </template>
      <template #[`item.updated_at`]="{ item }">
        <date-time :value="item.updated_at" />
      </template>

      <template #footer>
        <custom-data-table-footer />
      </template>
    </v-data-table>

    <add-group-dialog
      v-if="showAddGroupDialog"
      @cancel="closeAddGroupDialog()"
      @group-added="groupAdded()"
    />
  </titled-view>
</template>
<script lang="ts">
import { defineComponent } from 'vue';
import { DataTableHeader } from '@/ui-types';
import { mapActions, mapState, mapStores } from 'pinia';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { useNotifications } from '@/store/modules/notifications';
import { GlobalGroupResource } from '@/openapi-types';
import { Permissions, RouteName } from '@/global';
import AddGroupDialog from './AddGroupDialog.vue';
import { useUser } from '@/store/modules/user';
import TitledView from '@/components/ui/TitledView.vue';
import DateTime from '@/components/ui/DateTime.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';
import { XrdIconFolder } from '@niis/shared-ui';
import { VDataTable } from 'vuetify/labs/VDataTable';

export default defineComponent({
  name: 'GlobalResourcesList',
  components: {
    CustomDataTableFooter,
    DateTime,
    TitledView,
    AddGroupDialog,
    XrdIconFolder,
    VDataTable,
  },
  data() {
    return {
      showAddGroupDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroups, useNotifications),
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
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
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
      this.globalGroupStore.findAll().catch((error) => {
        this.showError(error);
      });
    },
  },
});
</script>
<style lang="scss" scoped>
@import '@/assets/tables';

.group-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
}
</style>
