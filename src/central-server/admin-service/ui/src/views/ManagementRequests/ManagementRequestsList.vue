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
        data-test="management-requests-table"
        class="elevation-0 data-table"
        item-key="id"
        :sort-by="sortBy"
        :loading="loading"
        :headers="headers"
        :must-sort="true"
        :items="managementRequests.items"
        :items-length="managementRequests.pagingOptions.total_items"
        :items-per-page="10"
        :items-per-page-options="itemsPerPageOptions"
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

<script lang="ts" setup>
import { useNotifications } from '@/store/modules/notifications';
import { useManagementRequests } from '@/store/modules/management-requests';
import ManagementRequestIdCell from '@/components/managementRequests/MrIdCell.vue';
import MrActionsCell from '@/components/managementRequests/MrActionsCell.vue';
import MrStatusCell from '@/components/managementRequests/MrStatusCell.vue';
import MrTypeCell from '@/components/managementRequests/MrTypeCell.vue';
import { DataQuery, DataTableHeader, SortItem } from '@/ui-types';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import DateTime from '@/components/ui/DateTime.vue';
import { computed, reactive, ref, watch } from 'vue';
import SearchableTitledView from '@/components/ui/SearchableTitledView.vue';
import { useI18n } from 'vue-i18n';
import { ManagementRequestStatus } from '@/openapi-types';
import { debounce } from '@/util/helpers';

const sortBy = [{ key: 'id', order: 'desc' }] as SortItem[];
const loading = ref(false);
const dataQuery = reactive({} as DataQuery);
const itemsPerPageOptions = defaultItemsPerPageOptions(50);

const managementRequests = useManagementRequests();
const { showError } = useNotifications();
const { t } = useI18n();

const showOnlyPending = computed({
  get(): boolean {
    return (
      managementRequests.currentFilter.status ===
      ManagementRequestStatus.WAITING
    );
  },
  set(value: boolean) {
    managementRequests.currentFilter.status = value
      ? ManagementRequestStatus.WAITING
      : undefined;
  },
});

const filterQuery = computed({
  get(): string {
    return managementRequests.currentFilter.query || '';
  },
  set(value: string) {
    managementRequests.currentFilter.query = value;
  },
});

const headers = computed(
  () =>
    [
      {
        title: t('global.id') as string,
        align: 'start',
        key: 'id',
      },
      {
        title: t('global.created') as string,
        align: 'start',
        key: 'created_at',
      },
      {
        title: t('global.type') as string,
        align: 'start',
        key: 'type',
      },
      {
        title: t('managementRequests.securityServerOwnerName') as string,
        align: 'start',
        key: 'security_server_owner',
      },
      {
        title: t('managementRequests.securityServerId') as string,
        align: 'start',
        key: 'security_server_id',
      },
      {
        title: t('global.status') as string,
        align: 'start',
        key: 'status',
      },
      {
        title: '',
        sortable: false,
        key: 'button',
      },
    ] as DataTableHeader[],
);

watch(
  filterQuery,
  debounce(() => {
    // Debounce is used to reduce unnecessary api calls
    fetchItems();
  }, 600),
);

async function changeOptions({ itemsPerPage, page, sortBy }) {
  dataQuery.itemsPerPage = itemsPerPage;
  dataQuery.page = page;
  dataQuery.sortBy = sortBy[0]?.key;
  dataQuery.sortOrder = sortBy[0]?.order;
  await fetchItems();
}

async function fetchItems() {
  loading.value = true;

  try {
    await managementRequests.find(dataQuery, managementRequests.currentFilter);
  } catch (error: unknown) {
    showError(error);
  } finally {
    loading.value = false;
  }
}
</script>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/tables' as *;
</style>
