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
      <router-link to="/settings/globalresources">
        <v-icon :color="colors.Purple100">mdi-chevron-left</v-icon>
        {{ $t('global.navigation.back') }}
      </router-link>
    </div>
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">{{ globalGroup.name }}</div>
      </div>
      <xrd-button data-test="remove-group-button"
        ><v-icon class="xrd-large-button-icon">mdi-close-circle</v-icon>
        {{ $t('globalGroup.deleteGroup') }}</xrd-button
      >
    </div>

    <info-card
      :title-text="$t('globalResources.description')"
      :info-text="globalGroup.description"
      data-test="global-group-description"
      :action-text="$t('action.edit')"
      @actionClicked="editDescription"
    />

    <!-- Toolbar buttons -->
    <div class="table-toolbar align-fix mt-8 pl-0">
      <div class="xrd-title-search align-fix mt-0 pt-0">
        <div class="xrd-view-title align-fix">
          {{ $t('globalGroup.groupMembers') }}
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
          ><v-icon class="xrd-large-button-icon">mdi-close-circle</v-icon>
          {{ $t('globalGroup.addMembers') }}</xrd-button
        >
      </div>
    </div>

    <!-- Table - Global Groups -->
    <v-data-table
      :loading="loading"
      :headers="globalGroupsHeaders"
      :items="globalGroups"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.memberName`]="{ item }">
        <div class="member-name xrd-clickable" @click="toDetails(item)">
          <xrd-icon-base class="mr-4"><XrdIconFolderOutline /></xrd-icon-base>
          <div>{{ item.memberName }}</div>
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
      title="globalResources.addGlobalGroup"
      @cancel="showFilterDialog = false"
    ></FilterDialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { DataTableHeader } from 'vuetify';
import InfoCard from '@/components/ui/InfoCard.vue';
import FilterDialog from '@/views/GlobalResources/GlobalGroup/GroupMembersFilterDialog.vue';

import { Colors } from '@/global';

/**
 * Global group view
 */
export default Vue.extend({
  components: {
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
      globalGroup: { description: 'uliuli', name: 'Group named X' },
      showFilterDialog: false,
      search: '',
      loading: false,
      globalGroups: [
        {
          memberName: 'Nordic Institute for Interoperability Solutions',
          type: 'Member',
          instance: 'DEV',
          class: 'ORG',
          code: '111',
          subsystem: 'subs',
          added: '2021-07-10 12:00',
        },

        {
          memberName: 'Organisaatio',
          type: 'Member',
          instance: 'DEV',
          class: 'COM',
          code: '222',
          subsystem: 'heips',
          added: '2020-01-12 11:00',
        },
      ],
    };
  },
  computed: {
    globalGroupsHeaders(): DataTableHeader[] {
      return [
        {
          text: this.$t('globalResources.code') as string,
          align: 'start',
          value: 'memberName',
          class: 'xrd-table-header ss-table-header-sercer-code',
        },
        {
          text: this.$t('globalResources.description') as string,
          align: 'start',
          value: 'type',
          class: 'xrd-table-header ss-table-header-owner-name',
        },
        {
          text: this.$t('globalResources.memberCount') as string,
          align: 'start',
          value: 'instance',
          class: 'xrd-table-header ss-table-header-owner-code',
        },
        {
          text: this.$t('globalResources.updated') as string,
          align: 'start',
          value: 'code',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
        {
          text: this.$t('globalResources.updated') as string,
          align: 'start',
          value: 'subsystem',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
        {
          text: this.$t('globalResources.updated') as string,
          align: 'start',
          value: 'added',
          class: 'xrd-table-header ss-table-header-owner-class',
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
  methods: {
    goBack(): void {
      this.$router.go(-1);
    },
    editDescription(): void {
      // Implement later
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
