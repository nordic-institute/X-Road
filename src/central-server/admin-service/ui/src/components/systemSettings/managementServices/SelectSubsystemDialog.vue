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
  <xrd-simple-dialog
    width="824"
    scrollable
    persistent
    title="systemSettings.selectSubsystem.title"
    save-button-text="action.select"
    z-index="1999"
    :disable-save="!selected || !changed"
    @cancel="cancel"
    @save="updateServiceProvider"
  >
    <template #content>
      <div style="height: 500px">
        <v-text-field
          v-model="search"
          :label="$t('systemSettings.selectSubsystem.search')"
          single-line
          hide-details
          class="search-input"
          autofocus
          variant="underlined"
          append-inner-icon="icon-Search"
          data-test="management-subsystem-search-field"
          @update:model-value="debouncedFetchItems"
        >
        </v-text-field>
        <!-- Table -->
        <v-data-table-server
          v-model="selectedSubsystems"
          data-test="subsystems-table"
          class="elevation-0 data-table xrd-table"
          item-value="client_id.encoded_id"
          show-select
          select-strategy="single"
          :loading="loading"
          :headers="headers"
          :items="selectableSubsystems"
          :items-length="totalItems"
          :page="pagingOptions.page"
          :items-per-page="pagingOptions.itemsPerPage"
          :items-per-page-options="itemsPerPageOptions"
          :loader-height="2"
          @update:options="changeOptions"
        >
        </v-data-table-server>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Client, PagedClients } from '@/openapi-types';
import { mapActions, mapStores } from 'pinia';
import { useClient } from '@/store/modules/clients';
import { useNotifications } from '@/store/modules/notifications';
import { debounce } from '@/util/helpers';
import { DataQuery, DataTableHeader, Event } from '@/ui-types';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import { VDataTableServer } from 'vuetify/labs/VDataTable';
import { useManagementServices } from '@/store/modules/management-services';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: { VDataTableServer },
  props: {
    currentSubsystemId: {
      type: String,
      required: true,
    },
  },
  emits: [Event.Cancel, Event.Select],
  data() {
    const itemsPerPageOptions = defaultItemsPerPageOptions(50);
    return {
      itemsPerPageOptions: itemsPerPageOptions,
      loading: false,
      pagingOptions: {
        page: 1,
        itemsPerPage: itemsPerPageOptions[0].value,
      } as DataQuery,
      clients: {} as PagedClients,
      search: '',
      selectedSubsystems: (this.currentSubsystemId
        ? [this.currentSubsystemId.replace('SUBSYSTEM:', '')]
        : []) as string[],
    };
  },
  computed: {
    ...mapStores(useClient, useManagementServices),
    totalItems(): number {
      return this.clients.paging_metadata?.total_items || 0;
    },
    selectableSubsystems(): Client[] {
      return this.clients.clients || [];
    },
    changed(): boolean {
      return (
        this.currentSubsystemId?.replace('SUBSYSTEM:', '') !==
        this.selectedSubsystems[0]
      );
    },
    selected(): boolean {
      return this.selectedSubsystems?.length === 1;
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('systemSettings.selectSubsystem.name') as string,
          align: 'start',
          key: 'member_name',
        },
        {
          title: this.$t('systemSettings.selectSubsystem.memberCode') as string,
          align: 'start',
          key: 'client_id.member_code',
        },
        {
          title: this.$t(
            'systemSettings.selectSubsystem.memberClass',
          ) as string,
          align: 'start',
          key: 'client_id.member_class',
        },
        {
          title: this.$t(
            'systemSettings.selectSubsystem.subsystemCode',
          ) as string,
          align: 'start',
          key: 'client_id.subsystem_code',
        },
        {
          title: this.$t(
            'systemSettings.selectSubsystem.xroadInstance',
          ) as string,
          align: 'start',
          key: 'client_id.instance_id',
        },
        {
          title: this.$t('systemSettings.selectSubsystem.type') as string,
          align: 'start',
          key: 'client_id.type',
        },
      ];
    },
  },
  created() {
    that = this;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.pagingOptions.page = 1;
      that.fetchClients();
    }, 600),
    fetchClients() {
      this.loading = true;
      this.clientStore
        .getByClientType('SUBSYSTEM', this.search, this.pagingOptions)
        .then((resp) => {
          this.clients = resp;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    changeOptions: async function ({ itemsPerPage, page, sortBy }) {
      this.pagingOptions.itemsPerPage = itemsPerPage;
      this.pagingOptions.page = page;
      this.pagingOptions.sortBy = sortBy[0]?.key;
      this.pagingOptions.sortOrder = sortBy[0]?.order;
      this.fetchClients();
    },
    cancel(): void {
      this.$emit(Event.Cancel);
    },
    updateServiceProvider(): void {
      this.loading = true;
      this.managementServicesStore
        .updateManagementServicesConfiguration({
          service_provider_id: this.selectedSubsystems[0],
        })
        .then(() => {
          this.showSuccess(
            this.$t('systemSettings.serviceProvider.changedSuccess'),
          );
          this.$emit(Event.Select, this.selectedSubsystems);
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';

.search-input {
  width: 300px;
}

.xrd-table {
  :deep(th span) {
    text-transform: uppercase;
  }

  :deep(td) {
    font-size: 14px;
  }
}
</style>
