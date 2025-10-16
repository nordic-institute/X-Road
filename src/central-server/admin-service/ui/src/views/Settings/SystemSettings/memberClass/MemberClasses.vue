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
  <XrdCard data-test="member-classes-list" title="systemSettings.memberClasses">
    <template #title-actions>
      <XrdBtn
        variant="outlined"
        data-test="system-settings-add-member-class-button"
        class="mr-4"
        prepend-icon="add_circle"
        text="action.add"
        @click="openEditMemberClassDialog()"
      />
    </template>
    <v-data-table
      v-model:sort-by="sortBy"
      item-key="code"
      class="xrd"
      no-data-text="noData.noMemberClasses"
      :headers="headers"
      :items="memberClasses"
      :must-sort="true"
      :items-per-page="itemsPerPageOptions[0].value"
      :items-per-page-options="itemsPerPageOptions"
      :loader-height="2"
    >
      <template #[`item.button`]="{ item }">
        <XrdBtn
          data-test="system-settings-delete-member-class-button"
          variant="text"
          color="tertiary"
          text="action.delete"
          @click="openDeleteMemberClassDialog(item)"
        />
        <XrdBtn
          data-test="system-settings-edit-member-class-button"
          variant="text"
          color="tertiary"
          text="action.edit"
          @click="openEditMemberClassDialog(item)"
        />
      </template>
      <template #bottom>
        <XrdPagination />
      </template>
    </v-data-table>
    <DeleteMemberClassDialog
      v-if="selectedMemberClass && showDeleteMemberClassDialog"
      :member-class="selectedMemberClass"
      @cancel="closeDeleteMemberClassDialog"
      @delete="closeDeleteMemberClassDialog"
    />
    <EditMemberClassDialog
      v-if="showAddEditMemberClassDialog"
      :member-class="selectedMemberClass"
      @cancel="closeEditMemberClassDialog"
      @save="closeEditMemberClassDialog"
    />
  </XrdCard>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { mapStores } from 'pinia';

import { XrdBtn, XrdCard, XrdPagination } from '@niis/shared-ui';

import { MemberClass } from '@/openapi-types';
import { useMemberClass } from '@/store/modules/member-class';
import { toPagingOptions } from '@/util/helpers';

import DeleteMemberClassDialog from './DeleteMemberClassDialog.vue';
import EditMemberClassDialog from './EditMemberClassDialog.vue';

export default defineComponent({
  components: {
    XrdBtn,
    XrdCard,
    EditMemberClassDialog,
    DeleteMemberClassDialog,
    XrdPagination,
  },
  data: () => ({
    sortBy: [{ key: 'code', order: 'asc' }] as SortItem[],
    selectedMemberClass: undefined as MemberClass | undefined,
    showAddEditMemberClassDialog: false,
    showDeleteMemberClassDialog: false,
  }),
  computed: {
    ...mapStores(useMemberClass),
    itemsPerPageOptions: () => toPagingOptions(5, 10, -1),
    memberClasses() {
      return this.memberClassStore.memberClasses;
    },
    totalItems(): number {
      return this.memberClassStore.memberClasses.length;
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('systemSettings.code') as string,
          align: 'start',
          key: 'code',
        },
        {
          title: this.$t('systemSettings.description') as string,
          align: 'start',
          key: 'description',
        },
        {
          title: '',
          key: 'button',
          align: 'end',
          sortable: false,
        },
      ];
    },
  },
  created() {
    this.memberClassStore.fetchAll();
  },
  methods: {
    openEditMemberClassDialog(
      memberClass: MemberClass | undefined = undefined,
    ) {
      this.selectedMemberClass = memberClass;
      this.showAddEditMemberClassDialog = true;
    },
    closeEditMemberClassDialog() {
      this.showAddEditMemberClassDialog = false;
      this.selectedMemberClass = undefined;
    },
    openDeleteMemberClassDialog(item: MemberClass) {
      this.showDeleteMemberClassDialog = true;
      this.selectedMemberClass = item;
    },
    closeDeleteMemberClassDialog() {
      this.showDeleteMemberClassDialog = false;
      this.selectedMemberClass = undefined;
    },
  },
});
</script>
