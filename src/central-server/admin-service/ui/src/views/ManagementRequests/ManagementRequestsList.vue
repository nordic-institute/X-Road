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
  <XrdView
    data-test="security-servers-view"
    title="tab.main.managementRequests"
  >
    <template #append-header>
      <div class="ml-6">
        <XrdSearchField
          v-model="filterQuery"
          data-test="search-query-field"
          width="320"
          :label="$t('action.search')"
        />
      </div>
      <v-spacer />
      <div class="only-pending">
        <v-switch
          v-model="showOnlyPending"
          data-test="show-only-pending-requests"
          class="xrd ml-3"
          false-icon="close"
          true-icon="check"
          hide-details
          inset
          :label="$t('managementRequests.showOnlyPending')"
          @update:model-value="fetchItems"
        />
      </div>
    </template>
    <v-data-table-server
      data-test="management-requests-table"
      class="xrd bg-surface-container xrd-rounded-16 border"
      item-key="id"
      :sort-by="sortBy"
      :loading="loading"
      :headers="headers"
      :must-sort="true"
      :items="managementRequests.items"
      :items-length="managementRequests.pagingOptions.total_items"
      :items-per-page="10"
      :loader-height="2"
      @update:options="changeOptions"
    >
      <template #[`item.id`]="{ item }">
        <XrdLabelWithIcon
          icon="rule_settings"
          semi-bold
          :clickable="canSeeDetails"
          :label="item.id"
          @navigate="item.id && navigateToDetails(item.id)"
        />
      </template>

      <template #[`item.created_at`]="{ item }">
        <div>
          <XrdDateTime :value="item.created_at" />
        </div>
      </template>

      <template #[`item.type`]="{ item }">
        <MrTypeCell :type="item.type" />
      </template>

      <template #[`item.security_server_owner`]="{ item }">
        <div>{{ item.security_server_owner }}</div>
      </template>

      <template #[`item.security_server_id`]="{ item }">
        <div>{{ item.security_server_id.encoded_id }}</div>
      </template>

      <template #[`item.status`]="{ item }">
        <MrStatusCell :status="item.status" />
      </template>

      <template #[`item.button`]="{ item }">
        <XrdBtn
          v-if="item.status === 'WAITING' && canApprove"
          data-test="approve-button"
          variant="text"
          text="action.approve"
          color="tertiary"
          @click="toApprove = item"
        />

        <XrdBtn
          v-if="item.status === 'WAITING' && canDecline"
          data-test="decline-button"
          variant="text"
          text="action.decline"
          color="tertiary"
          @click="toDecline = item"
        />
      </template>
      <template #bottom>
        <XrdPagination />
      </template>
    </v-data-table-server>
    <MrConfirmDialog
      v-if="toApprove?.id && toApprove.security_server_id.encoded_id"
      :request-id="toApprove.id"
      :security-server-id="toApprove.security_server_id.encoded_id"
      :new-member="newMember"
      @approve="fetchItems"
      @cancel="toApprove = undefined"
    />
    <MrDeclineDialog
      v-if="toDecline?.id && toDecline.security_server_id.encoded_id"
      :request-id="toDecline.id"
      :security-server-id="toDecline.security_server_id.encoded_id"
      @decline="fetchItems"
      @cancel="toDecline = undefined"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, reactive, ref, watch } from 'vue';

import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';

import {
  useNotifications,
  XrdBtn,
  XrdLabelWithIcon,
  XrdPagination,
  XrdView,
  XrdDateTime,
} from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import {
  ManagementRequestListView,
  ManagementRequestStatus,
  ManagementRequestType,
} from '@/openapi-types';
import { useManagementRequests } from '@/store/modules/management-requests';
import { useUser } from '@/store/modules/user';
import { DataQuery, PagingOptions } from '@/ui-types';
import { debounce } from '@/util/helpers';

import MrConfirmDialog from './dialogs/MrConfirmDialog.vue';
import MrDeclineDialog from './dialogs/MrDeclineDialog.vue';
import MrStatusCell from './MrStatusCell.vue';
import MrTypeCell from './MrTypeCell.vue';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort';

const sortBy = [{ key: 'id', order: 'desc' }] as SortItem[];
const loading = ref(false);
const toApprove = ref<ManagementRequestListView | undefined>(undefined);
const toDecline = ref<ManagementRequestListView | undefined>(undefined);
const dataQuery = reactive({} as DataQuery);

const managementRequests = useManagementRequests();
const { addError } = useNotifications();
const { t } = useI18n();
const router = useRouter();
const { hasPermission } = useUser();

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

const newClientOwner = computed(
  () =>
    toApprove.value?.type ===
      ManagementRequestType.CLIENT_REGISTRATION_REQUEST &&
    !toApprove.value?.client_owner_name,
);
const newServerOwner = computed(
  () =>
    toApprove.value?.type ===
      ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST &&
    !toApprove.value?.security_server_owner,
);
const newMember = computed(() => newClientOwner.value || newServerOwner.value);

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

const canSeeDetails = computed(() =>
  hasPermission(Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS),
);
const canApprove = computed(() =>
  hasPermission(Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS),
);
const canDecline = computed(() =>
  hasPermission(Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS),
);

watch(
  filterQuery,
  debounce(() => {
    // Debounce is used to reduce unnecessary api calls
    fetchItems();
  }, 600),
);

function navigateToDetails(reqId: number): void {
  router.push({
    name: RouteName.ManagementRequestDetails,
    params: { requestId: String(reqId) },
  });
}

async function changeOptions({ itemsPerPage, page, sortBy }: PagingOptions) {
  dataQuery.itemsPerPage = itemsPerPage;
  dataQuery.page = page;
  dataQuery.sortBy = sortBy[0]?.key;
  const order = sortBy[0]?.order;
  dataQuery.sortOrder =
    order === undefined
      ? undefined
      : order === true || order === 'asc'
        ? 'asc'
        : 'desc';
  await fetchItems();
}

async function fetchItems() {
  toApprove.value = undefined;
  toDecline.value = undefined;
  loading.value = true;

  try {
    await managementRequests.find(dataQuery, managementRequests.currentFilter);
  } catch (error: unknown) {
    addError(error);
  } finally {
    loading.value = false;
  }
}
</script>

<style lang="scss" scoped></style>
