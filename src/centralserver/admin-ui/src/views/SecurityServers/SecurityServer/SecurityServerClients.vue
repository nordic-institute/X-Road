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
  <div data-test="security-server-clients-view">
    <div id="clients-filter">
      <xrd-search v-model="search" />
    </div>
    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="members"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.name`]="{ item }">
        <div class="table-cell-name" @click="toDetails('netum')">
          <xrd-icon-base class="xrd-clickable mr-4"
            ><xrd-icon-folder-outline
          /></xrd-icon-base>

          {{ item.name }}
        </div>
      </template>

      <template #footer>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>
  </div>
</template>

<script lang="ts">
/**
 * View for 'security server clients' tab
 */
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';

export default Vue.extend({
  data() {
    return {
      search: '',
      loading: false,
      members: [
        {
          name: 'Nordic Institue for Interoperability Solutions',
          class: 'ORG',
          code: '555',
          subsystem: 'Subsystem X',
        },
        {
          name: 'Netum Oy',
          class: 'COM',
          code: 'IMAMEMBERCODE',
          subsystem: 'Subsystem B',
        },
      ],
    };
  },
  computed: {
    headers(): DataTableHeader[] {
      return [
        {
          text: (this.$t('global.memberName') as string) + ' (8)',
          align: 'start',
          value: 'name',
          class: 'xrd-table-header clients-table-header-name',
        },
        {
          text: this.$t('global.class') as string,
          align: 'start',
          value: 'class',
          class: 'xrd-table-header clients-table-header-class',
        },
        {
          text: this.$t('global.code') as string,
          align: 'start',
          value: 'code',
          class: 'xrd-table-header clients-table-header-code',
        },
        {
          text: this.$t('global.subsystem') as string,
          align: 'start',
          value: 'code',
          class: 'xrd-table-header clients-table-header-subsystem',
        },
      ];
    },
  },
  methods: {
    toDetails(): void {
      // Implement later
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.table-cell-name {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

#clients-filter {
  display: flex;
  justify-content: space-between;
}
</style>
