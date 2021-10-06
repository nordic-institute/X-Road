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
  <div data-test="global-resources-list-view">
    <!-- Title and action -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('globalResources.globalGroups') }}
      </div>

      <xrd-button
        data-test="add-certification-service"
        @click="showAddGroupDialog = true"
      >
        <xrd-icon-base class="xrd-large-button-icon"
          ><XrdIconAdd
        /></xrd-icon-base>
        {{ $t('globalResources.addGlobalGroup') }}</xrd-button
      >
    </div>

    <!-- Table 1 - Global Groups -->
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
      <template #[`item.code`]="{ item }">
        <div class="server-code xrd-clickable" @click="toDetails(item)">
          <xrd-icon-base class="mr-4"><XrdIconFolder /></xrd-icon-base>
          <div>{{ item.code }}</div>
        </div>
      </template>

      <template #footer>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Title and action -->
    <div class="table-toolbar align-fix mt-6 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('globalResources.centralServices') }}
      </div>

      <xrd-button data-test="add-certification-service" @click="() => {}">
        <xrd-icon-base class="xrd-large-button-icon"
          ><XrdIconAdd
        /></xrd-icon-base>
        {{ $t('globalResources.addCentralService') }}</xrd-button
      >
    </div>

    <!-- Table 2 - Central Services -->
    <v-data-table
      :loading="loading"
      :headers="centralServicesHeaders"
      :items="centralServices"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #footer>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Dialogs -->
    <xrd-simple-dialog
      v-if="showAddGroupDialog"
      :dialog="showAddGroupDialog"
      cancel-button-text="action.cancel"
      title="globalResources.addGlobalGroup"
      @cancel="showAddGroupDialog = false"
    >
      <template #content>
        <div class="dlg-input-width">
          <v-text-field
            v-model="code"
            outlined
            :label="$t('globalResources.code')"
            autofocus
            data-test="add-local-group-code-input"
          ></v-text-field>
        </div>

        <div class="dlg-input-width">
          <v-text-field
            v-model="description"
            hint
            :label="$t('globalResources.description')"
            outlined
            data-test="add-local-group-description-input"
          ></v-text-field>
        </div>
      </template>
    </xrd-simple-dialog>
  </div>
</template>

<script lang="ts">
/**
 * View for 'security servers' tab
 */
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import { RouteName } from '@/global';

export default Vue.extend({
  data() {
    return {
      search: '',
      loading: false,
      showOnlyPending: false,
      showAddGroupDialog: false,
      globalGroups: [
        {
          code: 'Security-server-owner_1',
          description: 'Security server owners HKI',
          memberCount: '11',
          updated: '2021-07-10 12:00',
        },

        {
          code: 'Security-server-owner_2',
          description: 'Security server owners TRE',
          memberCount: '3',
          updated: '2020-11-10 18:00',
        },
        {
          code: 'Security-server-owner_3',
          description: 'Descriptionish',
          memberCount: '9',
          updated: '2021-10-18 14:00',
        },
      ],

      centralServices: [
        {
          codeCentralService: '26766F',
          codeImplementingService: 'gfgjhg',
          version: '4.0',
          providerCode: '111',
          providerClass: 'COM',
          providerSubsystem: 'RESTSERVICE',
        },

        {
          codeCentralService: '645634-9',
          codeImplementingService: 'poujkh',
          version: '7',
          providerCode: '118',
          providerClass: 'ORG',
          providerSubsystem: 'Something',
        },
        {
          codeCentralService: '986435',
          codeImplementingService: 'data',
          version: '1',
          providerCode: '7777',
          providerClass: 'FI',
          providerSubsystem: 'RESTSERVICE',
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
          value: 'memberCount',
          class: 'xrd-table-header ss-table-header-owner-code',
        },
        {
          text: this.$t('globalResources.updated') as string,
          align: 'start',
          value: 'updated',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
      ];
    },

    centralServicesHeaders(): DataTableHeader[] {
      return [
        {
          text: this.$t('globalResources.codeCS') as string,
          align: 'start',
          value: 'codeCentralService',
          class: 'xrd-table-header ss-table-header-sercer-code',
        },
        {
          text: this.$t('globalResources.codeIS') as string,
          align: 'start',
          value: 'codeImplementingService',
          class: 'xrd-table-header ss-table-header-owner-name',
        },
        {
          text: this.$t('globalResources.version') as string,
          align: 'start',
          value: 'version',
          class: 'xrd-table-header ss-table-header-owner-code',
        },
        {
          text: this.$t('globalResources.providerCode') as string,
          align: 'start',
          value: 'providerCode',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
        {
          text: this.$t('globalResources.providerClass') as string,
          align: 'start',
          value: 'providerClass',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
        {
          text: this.$t('globalResources.providerSubsystem') as string,
          align: 'start',
          value: 'providerSubsystem',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
      ];
    },
  },

  methods: {
    // Add the type later when it exists
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    toDetails(globalGroup: any): void {
      this.$router.push({
        name: RouteName.GlobalGroup,
        params: { groupId: 'foo11' },
      });
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';

.server-code {
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
