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
    <div class="xrd-table-toolbar">
      <v-text-field
        v-model="search"
        label="Search"
        autofocus
        single-line
        hide-details
        class="search-input"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>

      <xrd-button
        v-if="showAddGroup"
        data-test="add-local-group-button"
        @click="addGroup"
        ><v-icon class="xrd-large-button-icon">icon-Add</v-icon>
        {{ $t('localGroups.addGroup') }}</xrd-button
      >
    </div>

    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="groups"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table mt-10"
      item-key="id"
      :loader-height="2"
      hide-default-footer
      :no-data-text="$t('noData.noLocalGroups')"
      data-test="local-groups-table"
    >
      <template #[`item.code`]="{ item }">
        <div class="group-code identifier-wrap" @click="viewGroup(item)">
          {{ item.code }}
        </div>
      </template>

      <template #[`item.updated_at`]="{ item }">
        {{ item.updated_at | formatDate }}
      </template>

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <newGroupDialog
      :id="id"
      :dialog="addGroupDialog"
      @cancel="closeDialog()"
      @group-added="groupAdded()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import * as api from '@/util/api';
import NewGroupDialog from './NewGroupDialog.vue';

import { Permissions, RouteName } from '@/global';
import { selectedFilter } from '@/util/helpers';
import { LocalGroup } from '@/openapi-types';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { useClientStore } from '@/store/modules/client';

export default Vue.extend({
  components: {
    NewGroupDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      search: '',
      dialog: false,
      groups: [] as LocalGroup[],
      addGroupDialog: false,
      loading: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useClientStore, ['client']),
    showAddGroup(): boolean {
      return this.hasPermission(Permissions.ADD_LOCAL_GROUP);
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('localGroups.code') as string,
          align: 'start',
          value: 'code',
          class: 'xrd-table-header lg-table-header-code',
        },
        {
          text: this.$t('localGroups.description') as string,
          align: 'start',
          value: 'description',
          class: 'xrd-table-header lg-table-header-description',
        },
        {
          text: this.$t('localGroups.memberCount') as string,
          align: 'start',
          value: 'member_count',
          class: 'xrd-table-header lg-table-header-member-count',
        },
        {
          text: this.$t('localGroups.updated') as string,
          align: 'start',
          value: 'updated_at',
          class: 'xrd-table-header lg-table-header-updated',
        },
      ];
    },
  },
  created() {
    this.fetchGroups(this.id);
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    addGroup(): void {
      this.addGroupDialog = true;
    },

    closeDialog(): void {
      this.addGroupDialog = false;
    },

    groupAdded(): void {
      this.fetchGroups(this.id);
      this.addGroupDialog = false;
    },

    filtered(): LocalGroup[] {
      return selectedFilter(this.groups, this.search, 'id');
    },

    viewGroup(group: LocalGroup): void {
      if (!group.id) {
        return;
      }
      this.$router.push({
        name: RouteName.LocalGroup,
        params: { clientId: this.id, groupId: group.id.toString() },
      });
    },

    fetchGroups(id: string): void {
      this.loading = true;
      api
        .get<LocalGroup[]>(`/clients/${encodePathParameter(id)}/local-groups`)
        .then((res) => {
          this.groups = res.data.sort((a: LocalGroup, b: LocalGroup) => {
            if (a.code.toLowerCase() < b.code.toLowerCase()) {
              return -1;
            }
            if (a.code.toLowerCase() > b.code.toLowerCase()) {
              return 1;
            }

            return 0;
          });
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => (this.loading = false));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

.group-code {
  color: $XRoad-Link;
  cursor: pointer;
}

.search-input {
  max-width: 300px;
}
</style>
