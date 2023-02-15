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
  <main id="management-requests-list" class="mt-0 mb-8">
    <div>
      <!-- Toolbar buttons -->
      <div class="table-toolbar align-fix mt-0 pl-0">
        <div class="xrd-title-search align-fix mt-0 pt-0">
          <div class="xrd-view-title align-fix">
            {{ $t('tab.main.managementRequests') }}
          </div>
          <xrd-search v-model="filter.query" class="margin-fix" />
          <xrd-filter class="ml-4 margin-fix" />
        </div>
        <div class="only-pending">
          <v-checkbox
            v-model="filter.status"
            :value="'WAITING'"
            :label="$t('managementRequests.showOnlyPending')"
            class="custom-checkbox"
          ></v-checkbox>
        </div>
      </div>

      <v-data-table
        :loading="loading"
        :headers="headers"
        :items="managementRequestsStore.items"
        :search="filter.query"
        :must-sort="true"
        :items-per-page="10"
        :options.sync="pagingSortingOptions"
        :server-items-length="managementRequestsStore.pagingOptions.total_items"
        class="elevation-0 data-table"
        item-key="id"
        :loader-height="2"
        :footer-props="{ itemsPerPageOptions: [10, 25, 50] }"
        @update:options="changeOptions"
      >
        <template #[`item.id`]="{ item }">
          <div class="xrd-clickable" @click="toDetails(item)">
            {{ item.id }}
          </div>
        </template>

        <template #[`item.created_at`]="{ item }">
          <div>{{ item.created_at | formatDateTime }}</div>
        </template>

        <template #[`item.type`]="{ item }">
          <type-cell :status="item.type" />
        </template>

        <template #[`item.security_server_owner`]="{ item }">
          <div>{{ item.security_server_owner }}</div>
        </template>

        <template #[`item.security_server_id`]="{ item }">
          <div>{{ item.security_server_id }}</div>
        </template>

        <template #[`item.status`]="{ item }">
          <status-cell :status="item.status" />
        </template>

        <template #[`item.button`]="{ item }">
          <div class="cs-table-actions-wrap management-requests-table">
            <div v-if="item.status === 'WAITING'">
              <xrd-button
                text
                :outlined="false"
                @click="openApproveConfirmationDialog(item)"
              >
                {{ $t('action.approve') }}
              </xrd-button>

              <xrd-button
                text
                :outlined="false"
                @click="openDeclineConfirmationDialog(item)"
              >
                {{ $t('action.decline') }}
              </xrd-button>
            </div>
          </div>
        </template>
      </v-data-table>

      <!-- Confirm approve dialog -->
      <xrd-confirm-dialog
        v-if="confirmApprove"
        :dialog="confirmApprove"
        title="managementRequests.approveRequest"
        text="managementRequests.confirmApprove"
        :data="{
          id: selectedManagementRequest.id,
          serverId: selectedManagementRequest.security_server_id,
        }"
        :loading="approvingRequest"
        @cancel="confirmApprove = false"
        @accept="approve(selectedManagementRequest.id)"
      />
      <!-- Confirm decline dialog -->
      <xrd-confirm-dialog
        v-if="confirmDecline"
        :dialog="confirmDecline"
        title="managementRequests.declineRequest"
        text="managementRequests.confirmApprove"
        :data="{
          id: selectedManagementRequest.id,
          serverId: selectedManagementRequest.security_server_id,
        }"
        :loading="decliningRequest"
        @cancel="confirmDecline = false"
        @accept="decline(selectedManagementRequest.id)"
      />
    </div>
  </main>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import { RouteName } from '@/global';
import StatusCell from '@/components/managementRequests/ManagementRequestStatusCell.vue';
import TypeCell from '@/components/managementRequests/ManagementRequestTypeCell.vue';
import { DataOptions, DataTableHeader } from 'vuetify';
import { mapActions, mapState, mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { debounce } from '@/util/helpers';
import XrdFilter from '@/components/ui/XrdFilter.vue';
import { managementRequestsStore } from '@/store/modules/managementRequestStore';
import { userStore } from '@/store/modules/user';
import { ManagementRequest, ManagementRequestsFilter } from '@/openapi-types';

export enum Scope {
  FULL,
  SECURITY_SERVER,
  MEMBER,
}

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

/**
 * General component for Management requests
 */
export default Vue.extend({
  name: 'ManagementRequestsList',
  components: {
    StatusCell,
    TypeCell,
    XrdFilter,
  },
  props: {
    scope: {
      type: Number as PropType<Scope>,
      default: Scope.FULL,
    },
  },
  data() {
    return {
      loading: false, //is data being loaded
      showOnlyPending: false,
      pagingSortingOptions: {} as DataOptions,
      filter: {} as ManagementRequestsFilter,
      selectedManagementRequest: undefined as undefined | ManagementRequest,
      confirmApprove: false,
      approvingRequest: false,
      confirmDecline: false,
      decliningRequest: false,
    };
  },
  computed: {
    ...mapStores(managementRequestsStore),
    ...mapState(userStore, ['hasPermission']),
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
          //TODO this should be removed from SS view
          text: this.$t('managementRequests.securityServerOwnerName') as string,
          align: 'start',
          value: 'security_server_owner',
          class: 'xrd-table-header mr-table-header-owner-name',
        },
        {
          //TODO this should be removed from SS view
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
  },
  watch: {
    filter: {
      handler(newValue, oldValue) {
        this.debouncedFetchItems();
      },
      deep: true,
    },
  },
  created() {
    that = this;
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.fetchItems(that.pagingSortingOptions);
    }, 600),
    toDetails(managementRequest: ManagementRequest): void {
      this.$router.push({
        name: RouteName.ManagementRequestDetails,
        params: {
          managementRequestId: String(managementRequest.id),
        },
      });
    },
    changeOptions: async function () {
      await this.fetchItems(this.pagingSortingOptions, this.filter);
    },
    fetchItems: async function (
      options: DataOptions,
      filter: ManagementRequestsFilter,
    ) {
      this.loading = true;
      try {
        await this.managementRequestsStore.find(options, this.filter);
      } catch (error: unknown) {
        this.showError(error);
      } finally {
        this.loading = false;
      }
    },
    openDeclineConfirmationDialog(managementRequest: ManagementRequest): void {
      this.selectedManagementRequest = managementRequest;
      this.confirmDecline = true;
    },
    openApproveConfirmationDialog(managementRequest: ManagementRequest): void {
      this.selectedManagementRequest = managementRequest;
      this.confirmApprove = true;
    },
    approve: async function (id: number) {
      await this.managementRequestsStore.approve(id);
    },
    decline: function (id: number) {
      this.managementRequestsStore.decline(id);
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

.management-requests-table {
  min-width: 182px;
}
</style>
