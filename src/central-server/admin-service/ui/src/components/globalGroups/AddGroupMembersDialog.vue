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
    title="globalGroup.dialog.addMembers.title"
    save-button-text="action.add"
    :loading="adding"
    :disable-save="anyClientsSelected"
    z-index="1999"
    scrollable
    @save="addMembers"
    @cancel="cancel"
  >
    <template #content>
      <div style="height: 500px">
        <!-- Table -->
        <v-data-table-server
          v-model="selectedClients"
          class="xrd-table elevation-0"
          data-test="select-members-list"
          item-value="client_id.encoded_id"
          show-select
          :loading="loading"
          :headers="headers"
          :items="selectableClients"
          :items-length="totalItems"
          :page="pagingOptions.page"
          :loader-height="2"
          :items-per-page-options="itemsPerPageOptions"
          @update:options="changeOptions"
        >
          <template #top>
            <v-text-field
              v-model="search"
              variant="underlined"
              data-test="member-subsystem-search-field"
              class="search-input"
              append-inner-icon="icon-Search"
              single-line
              hide-details
              autofocus
              :label="$t('systemSettings.selectSubsystem.search')"
              @update:model-value="debouncedFetchItems"
            />
          </template>
          <template #[`item.client_id.member_code`]="{ item }">
            <span data-test="code">
              {{ item.client_id.member_code }}
            </span>
          </template>
          <template #[`item.client_id.member_class`]="{ item }">
            <span data-test="class">
              {{ item.client_id.member_class }}
            </span>
          </template>
          <template #[`item.client_id.subsystem_code`]="{ item }">
            <span data-test="subsystem">
              {{ item.client_id.subsystem_code }}
            </span>
          </template>
          <template #[`item.client_id.instance_id`]="{ item }">
            <span data-test="instance">
              {{ item.client_id.instance_id }}
            </span>
          </template>
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
import { DataTableHeader, Event, PagingOptions } from '@/ui-types';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { debounce } from '@/util/helpers';
import { VDataTableServer } from 'vuetify/labs/VDataTable';
import { defaultItemsPerPageOptions } from '@/util/defaults';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: { VDataTableServer },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  emits: [Event.Add, Event.Cancel],
  data() {
    return {
      loading: false,
      adding: false,
      pagingOptions: { itemsPerPage: 10, page: 1 } as PagingOptions,
      itemsPerPageOptions: defaultItemsPerPageOptions(50),
      clients: {} as PagedClients,
      search: '',
      selectedClients: [],
    };
  },
  computed: {
    ...mapStores(useClient),
    ...mapStores(useGlobalGroups),
    anyClientsSelected(): boolean {
      return !this.selectedClients || this.selectedClients.length === 0;
    },
    totalItems(): number {
      return this.clients.paging_metadata?.total_items || 0;
    },
    selectableClients(): Client[] {
      return this.clients.clients || [];
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('systemSettings.selectSubsystem.name') as string,
          align: 'start',
          key: 'member_name',
          sortable: false,
        },
        {
          title: this.$t('systemSettings.selectSubsystem.memberCode') as string,
          align: 'start',
          key: 'client_id.member_code',
          sortable: false,
        },
        {
          title: this.$t(
            'systemSettings.selectSubsystem.memberClass',
          ) as string,
          align: 'start',
          key: 'client_id.member_class',
          sortable: false,
        },
        {
          title: this.$t(
            'systemSettings.selectSubsystem.subsystemCode',
          ) as string,
          align: 'start',
          key: 'client_id.subsystem_code',
          sortable: false,
        },
        {
          title: this.$t(
            'systemSettings.selectSubsystem.xroadInstance',
          ) as string,
          align: 'start',
          key: 'client_id.instance_id',
          sortable: false,
        },
        {
          title: this.$t('systemSettings.selectSubsystem.type') as string,
          align: 'start',
          key: 'client_id.type',
          sortable: false,
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
    async fetchClients() {
      this.loading = true;
      return this.clientStore
        .getByExcludingGroup(this.groupCode, this.search, this.pagingOptions)
        .then((resp) => {
          this.clients = resp;
        })
        .catch((error) => this.showError(error))
        .finally(() => (this.loading = false));
    },
    changeOptions(options: PagingOptions) {
      this.pagingOptions = options;
      this.fetchClients();
    },
    cancel(): void {
      if (this.adding) {
        return;
      }
      this.$emit(Event.Cancel);
    },
    addMembers(): void {
      this.adding = true;

      this.globalGroupStore
        .addGroupMembers(this.groupCode, this.selectedClients)
        .then((resp) => this.$emit(Event.Add, resp.data.items))
        .then(() => this.showSuccessMessage(this.selectedClients))
        .catch((error) => this.showError(error))
        .finally(() => (this.adding = false));
    },
    showSuccessMessage(identifiers: string[]) {
      this.showSuccess(
        this.$t('globalGroup.dialog.addMembers.success', {
          identifiers: identifiers.join(', '),
        }),
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.checkbox-column {
  width: 50px;
}

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
