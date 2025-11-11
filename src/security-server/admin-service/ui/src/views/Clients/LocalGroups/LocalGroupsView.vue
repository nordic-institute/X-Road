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
  <XrdSubView>
    <template #header>
      <XrdRoundedSearchField
        v-model="search"
        data-test="local-group-search-input"
        autofocus
        :label="$t('action.search')"
      />
      <v-spacer />
      <XrdBtn
        v-if="showAddGroup"
        data-test="add-local-group-button"
        prepend-icon="add_circle"
        text="localGroups.addGroup"
        @click="addGroup"
      />
    </template>

    <v-data-table
      data-test="local-groups-table"
      class="xrd xrd-rounded-12 border"
      no-data-text="noData.noLocalGroups"
      items-per-page="-1"
      hide-default-footer
      must-sort
      :loading="loadingLocalGroups"
      :headers="headers"
      :items="sortedLocalGroups"
      :search="search"
      :loader-height="2"
    >
      <template #[`item.code`]="{ item }">
        <XrdLabelWithIcon
          icon="group"
          :label="item.code"
          clickable
          @navigate="viewGroup(item)"
        />
      </template>

      <template #[`item.updated_at`]="{ item }">
        <XrdDate :value="item.updated_at" />
      </template>
    </v-data-table>

    <newGroupDialog
      v-if="addGroupDialog"
      :id="id"
      @cancel="closeDialog()"
      @group-added="groupAdded()"
    />
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import NewGroupDialog from './NewGroupDialog.vue';

import { Permissions, RouteName } from '@/global';
import { selectedFilter } from '@/util/helpers';
import { LocalGroup } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/client';
import {
  XrdDate,
  XrdSubView,
  XrdBtn,
  XrdLabelWithIcon,
  useNotifications,
} from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useLocalGroups } from '@/store/modules/local-groups';

export default defineComponent({
  components: {
    XrdSubView,
    NewGroupDialog,
    XrdBtn,
    XrdLabelWithIcon,
    XrdDate,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      search: '',
      dialog: false,
      groups: [] as LocalGroup[],
      addGroupDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useClient, ['client']),
    ...mapState(useLocalGroups, ['sortedLocalGroups', 'loadingLocalGroups']),
    showAddGroup(): boolean {
      return this.hasPermission(Permissions.ADD_LOCAL_GROUP);
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('localGroups.code') as string,
          align: 'start',
          key: 'code',
        },
        {
          title: this.$t('localGroups.description') as string,
          align: 'start',
          key: 'description',
        },
        {
          title: this.$t('localGroups.memberCount') as string,
          align: 'start',
          key: 'member_count',
        },
        {
          title: this.$t('localGroups.updated') as string,
          align: 'start',
          key: 'updated_at',
        },
      ];
    },
  },
  watch: {
    id: {
      immediate: true,
      handler() {
        this.fetchGroups(this.id);
      },
    },
  },
  methods: {
    ...mapActions(useLocalGroups, ['fetchLocalGroups']),
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
        params: {
          groupId: group.id.toString(),
        },
      });
    },

    fetchGroups(id: string): void {
      this.fetchLocalGroups(id).catch((error) => this.addError(error));
    },
  },
});
</script>

<style lang="scss" scoped>
:deep(.local-groups-table td) {
  white-space: normal;
  word-break: break-word;
  max-width: 250px;
}
</style>
