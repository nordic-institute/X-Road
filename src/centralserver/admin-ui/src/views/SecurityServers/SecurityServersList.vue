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
          {{ $t('tab.main.securityServers') }}
        </div>
        <xrd-search v-model="search" class="margin-fix" />
      </div>
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="securityServers"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.serverCode`]="{ item }">
        <div class="server-code xrd-clickable" @click="toDetails('netum')">
          <xrd-icon-base class="mr-4"><XrdIconSecurityServer /></xrd-icon-base>
          <div>{{ item.serverCode }}</div>
        </div>
      </template>

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>
  </sub-view-container>
</template>

<script lang="ts">
/**
 * View for 'security servers' tab
 */
import Vue from 'vue';
import SubViewContainer from '@/components/layout/SubViewContainer.vue';
import { DataTableHeader } from 'vuetify';
import { RouteName } from '@/global';

export default Vue.extend({
  components: {
    SubViewContainer,
  },
  data() {
    return {
      search: '',
      loading: false,
      showOnlyPending: false,
      securityServers: [
        {
          serverCode: '938726',
          serverOwnerName: 'Tartu Kesklinna Perearstikeskus OÜ',
          serverOnwerCode: '333',
          serverOnwerClass: 'DEV',
        },

        {
          serverCode: '12323',
          serverOwnerName: 'Tartu Kesklinna Perearstikeskus OÜ',
          serverOnwerCode: '444',
          serverOnwerClass: 'DEV',
        },
        {
          serverCode: '837478',
          serverOwnerName: 'Eesti Põllumajandusloomade Jõudluskontrolli ASi',
          serverOnwerCode: '444',
          serverOnwerClass: 'DEV',
        },
        {
          serverCode: '63533',
          serverOwnerName: 'Helsingin kristillisen koulun kannatusyhdistys',
          serverOnwerCode: '222',
          serverOnwerClass: 'FI',
        },
        {
          serverCode: '98370',
          serverOwnerName: 'Siseministeerium',
          serverOnwerCode: '999',
          serverOnwerClass: 'COM',
        },
        {
          serverCode: '63352',
          serverOwnerName: 'Turvallisuus- ja kemikaalivirasto',
          serverOnwerCode: '777',
          serverOnwerClass: 'COM',
        },
      ],
    };
  },
  computed: {
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('securityServers.serverCode') as string,
          align: 'start',
          value: 'serverCode',
          class: 'xrd-table-header ss-table-header-sercer-code',
        },
        {
          text: this.$t('securityServers.ownerName') as string,
          align: 'start',
          value: 'serverOwnerName',
          class: 'xrd-table-header ss-table-header-owner-name',
        },
        {
          text: this.$t('securityServers.ownerCode') as string,
          align: 'start',
          value: 'serverOnwerCode',
          class: 'xrd-table-header ss-table-header-owner-code',
        },
        {
          text: this.$t('securityServers.ownerClass') as string,
          align: 'start',
          value: 'serverOnwerClass',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
      ];
    },
  },

  methods: {
    // Add the type later when it exists
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    toDetails(securityServer: any): void {
      this.$router.push({
        name: RouteName.SecurityServerDetails,
        params: { id: 'foo11' },
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

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
