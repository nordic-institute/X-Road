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
    <div id="management-request-filters">
      <xrd-search
        v-model="search"
        class="search"
        :label="$t('global.search')"
      ></xrd-search>
      <v-checkbox
        class="show-only-waiting"
        :label="$t('members.member.managementRequests.showOnlyWaiting')"
        :background-color="colors.Purple100100"
        :input-value="showOnlyPending"
      />
    </div>
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
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Colors } from '@/global';
import StatusCell from '@/components/managementRequests/StatusCell.vue';
import TypeCell from '@/components/managementRequests/TypeCell.vue';
import { DataTableHeader } from 'vuetify';

/**
 * General component for Management requests
 */
export default Vue.extend({
  name: 'ManagementRequests',
  components: {
    StatusCell,
    TypeCell,
  },
  props: {
    managementRequests: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,
      search: '' as string,
      loading: false,
      showOnlyPending: false,
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

#management-request-filters {
  display: flex;
  justify-content: space-between;
}
</style>
