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
  <div data-test="member-classes-list">
    <!-- Table -->
    <v-data-table
      v-model:sort-by="sortBy"
      :headers="headers"
      :items="memberClasses"
      :must-sort="true"
      :items-per-page="itemsPerPageOptions[0].value"
      :items-per-page-options="itemsPerPageOptions"
      class="elevation-0 data-table xrd-table"
      item-key="code"
      :loader-height="2"
      :no-data-text="$t('noData.noMemberClasses')"
    >
      <template #top>
        <data-table-toolbar title-key="systemSettings.memberClasses">
          <xrd-button
            outlined
            class="mr-4"
            data-test="system-settings-add-member-class-button"
            @click="openEditMemberClassDialog()"
          >
            <xrd-icon-base class="xrd-large-button-icon">
              <xrd-icon-add />
            </xrd-icon-base>
            {{ $t('action.add') }}
          </xrd-button>
        </data-table-toolbar>
      </template>

      <template #[`item.button`]="{ item }">
        <div class="cs-table-actions-wrap">
          <xrd-button
            text
            data-test="system-settings-edit-member-class-button"
            @click="openEditMemberClassDialog(item)"
          >
            {{ $t('action.edit') }}
          </xrd-button>

          <xrd-button
            text
            data-test="system-settings-delete-member-class-button"
            @click="openDeleteMemberClassDialog(item)"
          >
            {{ $t('action.delete') }}
          </xrd-button>
        </div>
      </template>
    </v-data-table>

    <delete-member-class-dialog
      v-if="selectedMemberClass && showDeleteMemberClassDialog"
      :member-class="selectedMemberClass"
      @cancel="closeDeleteMemberClassDialog"
      @delete="closeDeleteMemberClassDialog"
    />
    <edit-member-class-dialog
      v-if="showAddEditMemberClassDialog"
      :member-class="selectedMemberClass"
      @cancel="closeEditMemberClassDialog"
      @edit="closeEditMemberClassDialog"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { MemberClass } from '@/openapi-types';
import { mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useMemberClass } from '@/store/modules/member-class';
import EditMemberClassDialog from '@/components/systemSettings/EditMemberClassDialog.vue';
import DeleteMemberClassDialog from '@/components/systemSettings/DeleteMemberClassDialog.vue';
import { DataTableHeader } from '@/ui-types';
import { VDataTable } from 'vuetify/labs/VDataTable';
import DataTableToolbar from '@/components/ui/DataTableToolbar.vue';
import { toPagingOptions } from '@/util/helpers';

export default defineComponent({
  components: {
    DataTableToolbar,
    EditMemberClassDialog,
    DeleteMemberClassDialog,
    VDataTable,
  },
  data: () => ({
    sortBy: [{ key: 'code', order: 'asc' }],
    selectedMemberClass: undefined as MemberClass | undefined,
    showAddEditMemberClassDialog: false,
    showDeleteMemberClassDialog: false,
  }),
  computed: {
    ...mapStores(useMemberClass, useNotifications),
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

<style lang="scss" scoped>
@import '@/assets/tables';

.server-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}

.align-fix {
  align-items: center;
}

.card-corner-button {
  display: flex;
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.title-cell {
  max-width: 40%;
  width: 40%;
}

.action-cell {
  text-align: right;
  width: 100px;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}
</style>
