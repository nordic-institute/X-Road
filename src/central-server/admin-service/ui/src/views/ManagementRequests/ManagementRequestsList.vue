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
    <searchable-titled-view
      v-model="filterQuery"
      title-key="tab.main.managementRequests"
    >
      <template #header-buttons>
        <div class="only-pending">
          <v-checkbox
            v-model="showOnlyPending"
            density="compact"
            :label="$t('managementRequests.showOnlyPending')"
            class="custom-checkbox"
            data-test="show-only-pending-requests"
            hide-details
            @update:model-value="fetchItems"
          />
        </div>
      </template>
      <v-data-table-server
        v-model:sort-by="sortBy"
        data-test="management-requests-table"
        :loading="loading"
        :headers="headers"
        :must-sort="true"
        :items="managementRequestsStore.items"
        :items-length="managementRequestsStore.pagingOptions.total_items"
        :items-per-page="10"
        :items-per-page-options="itemsPerPageOptions"
        class="elevation-0 data-table"
        item-key="id"
        :loader-height="2"
        @update:options="changeOptions"
      >
        <template #[`item.id`]="{ item }">
          <management-request-id-cell :management-request="item" />
        </template>

        <template #[`item.created_at`]="{ item }">
          <div>
            <date-time :value="item.created_at" />
          </div>
        </template>

        <template #[`item.type`]="{ item }">
          <mr-type-cell :type="item.type" />
        </template>

        <template #[`item.security_server_owner`]="{ item }">
          <div>{{ item.security_server_owner }}</div>
        </template>

        <template #[`item.security_server_id`]="{ item }">
          <div>{{ item.security_server_id.encoded_id }}</div>
        </template>

        <template #[`item.status`]="{ item }">
          <mr-status-cell :status="item.status" />
        </template>

        <template #[`item.button`]="{ item }">
          <mr-actions-cell
            :management-request="item"
            @approve="fetchItems"
            @decline="fetchItems"
          />
        </template>
      </v-data-table-server>
    </searchable-titled-view>
  </div>
</template>

<script lang="ts">
import { VDataTableServer } from 'vuetify/labs/VDataTable';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { debounce } from '@/util/helpers';
import { useManagementRequests } from '@/store/modules/management-requests';
import { ManagementRequestStatus } from '@/openapi-types';
import ManagementRequestIdCell from '@/components/managementRequests/MrIdCell.vue';
import MrActionsCell from '@/components/managementRequests/MrActionsCell.vue';
import MrStatusCell from '@/components/managementRequests/MrStatusCell.vue';
import MrTypeCell from '@/components/managementRequests/MrTypeCell.vue';
import { DataQuery, DataTableHeader } from '@/ui-types';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import DateTime from '@/components/ui/DateTime.vue';
import { defineComponent } from 'vue';
import SearchableTitledView from '@/components/ui/SearchableTitledView.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

/**
 * General component for Management requests
 */
export default defineComponent({
  name: 'ManagementRequestsList',
  components: {
    SearchableTitledView,
    DateTime,
    MrTypeCell,
    MrStatusCell,
    MrActionsCell,
    ManagementRequestIdCell,
    VDataTableServer,
  },
  data() {
    return {
      sortBy: [{ key: 'id', order: 'desc' }],
      loading: false, //is data being loaded
      dataQuery: {} as DataQuery,
      itemsPerPageOptions: defaultItemsPerPageOptions(50),
    };
  },
  computed: {
    ...mapStores(useManagementRequests),
    showOnlyPending: {
      get(): boolean {
        return (
          this.managementRequestsStore.currentFilter.status ===
          ManagementRequestStatus.WAITING
        );
      },
      set(value: boolean) {
        this.managementRequestsStore.pagingSortingOptions.page = 1;
        this.managementRequestsStore.currentFilter.status = value
          ? ManagementRequestStatus.WAITING
          : undefined;
      },
    },
    filterQuery: {
      get(): string {
        return this.managementRequestsStore.currentFilter.query || '';
      },
      set(value: string) {
        this.managementRequestsStore.pagingSortingOptions.page = 1;
        this.managementRequestsStore.currentFilter.query = value;
      },
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('global.id') as string,
          align: 'start',
          key: 'id',
        },
        {
          title: this.$t('global.created') as string,
          align: 'start',
          key: 'created_at',
        },
        {
          title: this.$t('global.type') as string,
          align: 'start',
          key: 'type',
        },
        {
          title: this.$t(
            'managementRequests.securityServerOwnerName',
          ) as string,
          align: 'start',
          key: 'security_server_owner',
        },
        {
          title: this.$t('managementRequests.securityServerId') as string,
          align: 'start',
          key: 'security_server_id',
        },
        {
          title: this.$t('global.status') as string,
          align: 'start',
          key: 'status',
        },
        {
          title: '',
          sortable: false,
          key: 'button',
        },
      ];
    },
  },
  watch: {
    filterQuery: {
      handler() {
        this.debouncedFetchItems();
      },
    },
  },
  created() {
    that = this;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.fetchItems();
    }, 600),
    changeOptions: async function ({ itemsPerPage, page, sortBy }) {
      this.dataQuery.itemsPerPage = itemsPerPage;
      this.dataQuery.page = page;
      this.dataQuery.sortBy = sortBy[0]?.key;
      this.dataQuery.sortOrder = sortBy[0]?.order;
      await this.fetchItems();
    },
    fetchItems: async function () {
      this.loading = true;

      try {
        await this.managementRequestsStore.find(
          this.dataQuery,
          this.managementRequestsStore.currentFilter,
        );
      } catch (error: unknown) {
        this.showError(error);
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.custom-checkbox {
  .v-label {
    font-size: 14px;
  }
}
</style>
