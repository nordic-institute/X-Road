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
    <header class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('globalResources.globalGroups') }}
      </div>

      <xrd-button
        v-if="allowAddGlobalGroup"
        data-test="add-global-group-button"
        @click="showAddGroupDialog = true"
      >
        <xrd-icon-base class="xrd-large-button-icon">
          <XrdIconAdd />
        </xrd-icon-base>
        {{ $t('globalResources.addGlobalGroup') }}
      </xrd-button>
    </header>

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
        {{ item.updated_at | formatDateTime }}
      </template>

      <template #footer>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>

    <add-group-dialog
      :dialog="showAddGroupDialog"
      @cancel="closeAddGroupDialog()"
      @group-added="groupAdded()"
    />
  </section>
</template>
<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import { mapActions, mapState, mapStores } from 'pinia';
import { useGlobalGroupsStore } from '@/store/modules/global-groups';
import { notificationsStore } from '@/store/modules/notifications';
import { GlobalGroupResource } from '@/openapi-types';
import { Permissions, RouteName } from '@/global';
import AddGroupDialog from './AddGroupDialog.vue';
import { userStore } from '@/store/modules/user';

export default Vue.extend({
  name: 'GlobalResourcesList',
  components: { AddGroupDialog },
  data() {
    return {
      showAddGroupDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroupsStore, notificationsStore),
    ...mapState(userStore, ['hasPermission']),
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
          text: this.$t('globalResources.code') as string,
          align: 'start',
          value: 'code',
          class: 'xrd-table-header ss-table-header-sercer-code',
        },
        {
          text: this.$t('globalResources.description') as string,
          align: 'start',
          value: 'description',
          class: 'xrd-table-header ss-table-header-owner-name',
        },
        {
          text: this.$t('globalResources.memberCount') as string,
          align: 'start',
          value: 'member_count',
          class: 'xrd-table-header ss-table-header-owner-code',
        },
        {
          text: this.$t('globalResources.updated') as string,
          align: 'start',
          value: 'updated_at',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
      ];
    },
  },
  created() {
    this.fetchAllGroups();
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
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
@import '~styles/tables';

.group-code {
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
</style>
