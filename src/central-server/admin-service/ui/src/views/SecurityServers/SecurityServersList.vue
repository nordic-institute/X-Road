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
  <XrdView data-test="security-servers-view" title="tab.main.securityServers">
    <template #append-header>
      <div class="ml-6">
        <XrdSearchField
          v-model="search"
          data-test="search-query-field"
          width="320"
          :label="$t('action.search')"
          @update:model-value="debouncedFindServers"
        />
      </div>
    </template>
    <v-data-table-server
      class="xrd bg-surface-container xrd-rounded-16"
      item-key="server_id.server_code"
      disable-filtering
      :loading="loading"
      :headers="headers"
      :items="securityServerStore.securityServers"
      :items-length="securityServerStore.securityServerPagingOptions.total_items"
      :must-sort="true"
      :items-per-page="10"
      :no-data-text="emptyListReasoning"
      :loader-height="2"
      @update:options="findServers"
    >
      <template #[`item.server_id.server_code`]="{ item }">
        <XrdLabelWithIcon icon="dns" semi-bold clickable :label="item.server_id.server_code" @navigate="toDetails(item)" />
      </template>
      <template #[`item.in_maintenance_mode`]="{ item }">
        <v-icon v-if="item.in_maintenance_mode" class="mr-2" icon="check_circle filled" color="success" />
        {{ item.maintenance_mode_message }}
      </template>
      <template #bottom>
        <XrdPagination />
      </template>
    </v-data-table-server>
  </XrdView>
</template>

<script lang="ts">
/**
 * View for 'security servers' tab
 */
import { defineComponent } from 'vue';
import { RouteName } from '@/global';
import { SecurityServer } from '@/openapi-types';
import { useSecurityServer } from '@/store/modules/security-servers';
import { mapStores } from 'pinia';
import { debounce } from '@/util/helpers';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import { DataQuery, PagingOptions } from '@/ui-types';
import { XrdView, XrdPagination, XrdLabelWithIcon, useNotifications } from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    XrdPagination,
    XrdView,
    XrdLabelWithIcon,
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      search: '',
      loading: false,
      showOnlyPending: false,
      dataQuery: {} as DataQuery,
      itemsPerPageOptions: defaultItemsPerPageOptions(),
    };
  },
  computed: {
    ...mapStores(useSecurityServer),
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('securityServers.serverCode') as string,
          align: 'start',
          key: 'server_id.server_code',
        },
        {
          title: this.$t('securityServers.ownerName') as string,
          align: 'start',
          key: 'owner_name',
        },
        {
          title: this.$t('securityServers.ownerCode') as string,
          align: 'start',
          key: 'server_id.member_code',
        },
        {
          title: this.$t('securityServers.ownerClass') as string,
          align: 'start',
          key: 'server_id.member_class',
        },
        {
          title: this.$t('securityServers.maintenanceMode') as string,
          align: 'start',
          key: 'in_maintenance_mode',
        },
      ];
    },
    emptyListReasoning(): string {
      return this.search ? 'noData.noMatches' : 'noData.noSecurityServers';
    },
  },

  created() {
    //eslint-disable-next-line @typescript-eslint/no-this-alias
    that = this;
  },
  methods: {
    debouncedFindServers: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.fetchServers();
    }, 600),
    toDetails(securityServer: SecurityServer): void {
      this.$router.push({
        name: RouteName.SecurityServerDetails,
        params: { serverId: securityServer.server_id.encoded_id || '' },
      });
    },

    findServers: async function ({ itemsPerPage, page, sortBy }: PagingOptions) {
      this.dataQuery.itemsPerPage = itemsPerPage;
      this.dataQuery.page = page;
      this.dataQuery.sortBy = sortBy[0]?.key;
      const order = sortBy[0]?.order;
      this.dataQuery.sortOrder = order === undefined ? undefined : order === true || order === 'asc' ? 'asc' : 'desc';
      this.fetchServers();
    },
    fetchServers: async function () {
      this.loading = true;
      this.dataQuery.search = this.search;
      try {
        await this.securityServerStore.find(this.dataQuery);
      } catch (error: unknown) {
        this.addError(error);
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>
<style lang="scss" scoped></style>
