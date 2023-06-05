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
    <!-- Toolbar buttons -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-title-search align-fix mt-0 pt-0">
        <div class="xrd-view-title align-fix">
          {{ $t('tab.main.managementRequests') }}
        </div>
        <xrd-search
          v-model="filterQuery"
          class="margin-fix"
          data-test="management-requests-search"
        />
        <!-- Not yet implemented -->
        <!--<xrd-filter class="ml-4 margin-fix" />-->
      </div>
      <div class="only-pending">
        <v-checkbox
          v-model="showOnlyPending"
          :label="$t('managementRequests.showOnlyPending')"
          class="custom-checkbox"
          data-test="show-only-pending-requests"
          @change="changeOptions"
        ></v-checkbox>
      </div>
    </div>

    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="managementRequestsStore.items"
      :search="managementRequestsStore.currentFilter.query"
      :must-sort="true"
      :items-per-page="10"
      :options.sync="managementRequestsStore.pagingSortingOptions"
      :server-items-length="managementRequestsStore.pagingOptions.total_items"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      :footer-props="{ itemsPerPageOptions: [10, 25, 50] }"
      data-test="management-requests-table"
      @update:options="changeOptions"
    >
      <template #[`item.id`]="{ item }">
        <management-request-id-cell :management-request="item" />
      </template>

      <template #[`item.created_at`]="{ item }">
        <div>{{ item.created_at | formatDateTime }}</div>
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
          @approve="changeOptions"
          @decline="changeOptions"
        />
      </template>
    </v-data-table>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import { mapActions, mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { debounce } from '@/util/helpers';
import { managementRequestsStore } from '@/store/modules/managementRequestStore';
import { ManagementRequestStatus } from '@/openapi-types';
import ManagementRequestIdCell from '@/components/managementRequests/MrIdCell.vue';
import MrActionsCell from '@/components/managementRequests/MrActionsCell.vue';
import MrStatusCell from '@/components/managementRequests/MrStatusCell.vue';
import MrTypeCell from '@/components/managementRequests/MrTypeCell.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

/**
 * General component for Management requests
 */
export default Vue.extend({
  name: 'ManagementRequestsList',
  components: {
    MrTypeCell,
    MrStatusCell,
    MrActionsCell,
    ManagementRequestIdCell,
  },
  data() {
    return {
      loading: false, //is data being loaded
    };
  },
  computed: {
    ...mapStores(managementRequestsStore),
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
          text: this.$t('managementRequests.securityServerOwnerName') as string,
          align: 'start',
          value: 'security_server_owner',
          class: 'xrd-table-header mr-table-header-owner-name',
        },
        {
          text: this.$t('managementRequests.securityServerId') as string,
          align: 'start',
          value: 'security_server_id',
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
  },
  watch: {
    filterQuery: {
      handler(newValue, oldValue) {
        this.debouncedFetchItems();
      },
    },
  },
  created() {
    that = this;
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.fetchItems();
    }, 600),
    changeOptions: async function () {
      await this.fetchItems();
    },
    fetchItems: async function () {
      this.loading = true;

      try {
        await this.managementRequestsStore.find(
          this.managementRequestsStore.pagingSortingOptions,
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
@import '~@/assets/tables';

#management-request-filters {
  display: flex;
  justify-content: space-between;
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
