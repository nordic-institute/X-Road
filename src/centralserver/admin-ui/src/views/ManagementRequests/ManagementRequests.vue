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
  <sub-view-container>
    <!-- Toolbar buttons -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-title-search align-fix mt-0 pt-0">
        <div class="xrd-view-title align-fix">
          {{ $t('tab.main.managementRequests') }}
        </div>
        <xrd-search v-model="search" class="margin-fix" />
        <xrd-filter v-model="search" class="ml-4 margin-fix" />
      </div>
      <div class="only-pending">
        <v-checkbox
          v-model="showOnlyPending"
          :label="$t('managementRequests.showOnlyPending')"
          class="custom-checkbox"
        ></v-checkbox>
      </div>
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="managementRequests"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.id`]="{ item }">
        <div class="request-id">{{ item.id }}</div>
      </template>

      <template #[`item.type`]="{ item }">
        <type-cell :status="item.type" />
      </template>

      <template #[`item.status`]="{ item }">
        <status-cell :status="item.status" />
      </template>

      <template #[`item.button`]>
        <div class="cs-table-actions-wrap">
          <xrd-button text :outlined="false">{{
            $t('action.approve')
          }}</xrd-button>

          <xrd-button text :outlined="false">{{
            $t('action.decline')
          }}</xrd-button>
        </div>
      </template>

      <template #footer>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>
  </sub-view-container>
</template>

<script lang="ts">
/**
 * View for 'management requests' tab
 */
import Vue from 'vue';
import SubViewContainer from '@/components/layout/SubViewContainer.vue';
import StatusCell from '../../components/managementRequests/StatusCell.vue';
import TypeCell from '../../components/managementRequests/TypeCell.vue';
import XrdFilter from './XrdFilter.vue';
import { DataTableHeader } from 'vuetify';

export default Vue.extend({
  components: {
    SubViewContainer,
    StatusCell,
    TypeCell,
    XrdFilter,
  },
  data() {
    return {
      search: '' as string,
      loading: false,
      showOnlyPending: false,
      managementRequests: [
        {
          id: '938726',
          created: '2021-02-01',
          type: 'change_owner',
          serverOwnerName: 'Tartu Kesklinna Perearstikeskus OÜ',
          serverOnwerId: 'DEV-333',
          serverCode: 'sidecar',
          status: 'APPROVED',
        },
        {
          id: '736287',
          created: '2021-05-05',
          type: 'delete_certificate',
          serverOwnerName: 'Eesti Põllumajandusloomade Jõudluskontrolli ASi',
          serverOnwerId: 'COM-777',
          serverCode: 'SS1',
          status: 'REJECTED',
        },
        {
          id: '234234',
          created: '2021-03-12',
          type: 'delete_client',
          serverOwnerName: 'Helsingin kristillisen koulun kannatusyhdistys',
          serverOnwerId: 'COM-666',
          serverCode: 'SS2',
          status: 'PENDING',
        },
        {
          id: '987283',
          created: '2021-04-22',
          type: 'register_certificate',
          serverOwnerName: 'Siseministeerium',
          serverOnwerId: 'DEV-444',
          serverCode: 'SS2',
          status: 'APPROVED',
        },
        {
          id: '123235',
          created: '2021-01-21',
          type: 'register_client',
          serverOwnerName: 'Turvallisuus- ja kemikaalivirasto',
          serverOnwerId: 'COM-555',
          serverCode: 'dev-toolkit-confidential.i.x-road',
          status: 'PENDING',
        },
      ],
    };
  },
  computed: {
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('global.id') as string,
          align: 'start',
          value: 'id',
          class: 'xrd-table-header mr-table-header-id',
        },
        {
          text: this.$t('global.created') as string,
          align: 'start',
          value: 'created',
          class: 'xrd-table-header mr-table-header-created',
        },
        {
          text: this.$t('global.type') as string,
          align: 'start',
          value: 'type',
          class: 'xrd-table-header mr-table-header-type',
        },

        {
          text: this.$t('managementRequests.serverOwnerName') as string,
          align: 'start',
          value: 'serverOwnerName',
          class: 'xrd-table-header mr-table-header-owner-name',
        },
        {
          text: this.$t('managementRequests.serverOnwerId') as string,
          align: 'start',
          value: 'serverOnwerId',
          class: 'xrd-table-header mr-table-header-owner-id',
        },
        {
          text: this.$t('managementRequests.serverCode') as string,
          align: 'start',
          value: 'serverCode',
          class: 'xrd-table-header mr-table-header-server-code',
        },

        {
          text: this.$t('global.status') as string,
          align: 'start',
          value: 'status',
          class: 'xrd-table-header mr-table-header-status',
        },

        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header mr-table-header-buttons',
        },
      ];
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';

.request-id {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.custom-checkbox {
  .v-label {
    font-size: 14px;
  }
}

.only-pending {
  display: flex;
  justify-content: flex-end;
}
</style>
