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
  <xrd-sub-view-container>
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
      :items="getManagementRequests"
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

      <template #[`item.created_at`]="{ item }">
        <div>{{ item.created_at | formatDateTime }}</div>
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
  </xrd-sub-view-container>
</template>

<script lang="ts">
/**
 * View for 'management requests' tab
 */
import Vue from 'vue';
import StatusCell from '../../components/managementRequests/StatusCell.vue';
import TypeCell from '../../components/managementRequests/TypeCell.vue';
import XrdFilter from '../../components/ui/XrdFilter.vue';
import { DataTableHeader } from 'vuetify';
import { mapState } from 'pinia';
import { useManagementRequests } from '@/store/modules/managementRequests';

export default Vue.extend({
  components: {
    StatusCell,
    TypeCell,
    XrdFilter,
  },
  data() {
    return {
      search: '' as string,
      loading: false,
      showOnlyPending: false,
      managementRequestsMockData: [
        {
          id: 13,
          type: 'AUTH_CERT_REGISTRATION_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Tartu Kesklinna Perearstikeskus OÜ',
          security_server_id: {
            instance_id: 'DEV7X',
            type: 'SERVER',
            member_class: 'TST',
            member_code: 'MEMBER1',
            server_code: 'RH1',
          },
          status: 'APPROVED',
          created_at: '2021-07-07T10:09:42.10186Z',
        },
        {
          id: 736287,
          type: 'CLIENT_REGISTRATION_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Eesti Põllumajandusloomade Jõudluskontrolli ASi',
          security_server_id: {
            instance_id: 'DEV9X',
            type: 'SERVER',
            member_class: 'TST',
            member_code: 'MEMBER22',
            server_code: 'RH3',
          },
          status: 'REJECTED',
          created_at: '2021-02-08T10:09:40.10186Z',
        },
        {
          id: 64,
          type: 'OWNER_CHANGE_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Helsingin kristillisen koulun kannatusyhdistys',
          security_server_id: {
            instance_id: 'OPP',
            type: 'SERVER',
            member_class: 'RAA',
            member_code: 'MEMBER7',
            server_code: 'X1',
          },
          status: 'PENDING',
          created_at: '2021-03-11T10:09:40.10186Z',
        },
        {
          id: 112283,
          type: 'CLIENT_DELETION_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Siseministeerium',
          security_server_id: {
            instance_id: 'WAP',
            type: 'SERVER',
            member_class: 'MOP',
            member_code: 'MEM227',
            server_code: 'K8',
          },
          status: 'APPROVED',
          created_at: '2020-12-13T10:09:40.10186Z',
        },
        {
          id: 947283,
          type: 'AUTH_CERT_DELETION_REQUEST',
          origin: 'CENTER',
          server_owner_name: 'Turvallisuus- ja kemikaalivirasto',
          security_server_id: {
            instance_id: 'NEO',
            type: 'SERVER',
            member_class: 'AUS',
            member_code: 'MEMBER9',
            server_code: 'SR2',
          },
          status: 'PENDING',
          created_at: '2020-12-13T10:09:40.10186Z',
        },
      ],
    };
  },
  computed: {
    ...mapState(useManagementRequests, ['getManagementRequests']),
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
          value: 'created_at',
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
          value: 'server_owner_name',
          class: 'xrd-table-header mr-table-header-owner-name',
        },
        {
          text: this.$t('managementRequests.serverIdentifier') as string,
          align: 'start',
          value: 'displayedServerId',
          class: 'xrd-table-header mr-table-header-owner-id',
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
