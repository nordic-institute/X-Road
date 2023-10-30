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
  <div data-test="security-servers-view">
    <searchable-titled-view
      v-model="search"
      title-key="tab.main.securityServers"
    >
      <v-data-table-server
        :loading="loading"
        :headers="headers"
        :items="securityServerStore.securityServers"
        :items-per-page-options="itemsPerPageOptions"
        :items-length="
          securityServerStore.securityServerPagingOptions.total_items
        "
        :must-sort="true"
        :items-per-page="10"
        disable-filtering
        class="elevation-0 data-table"
        :no-data-text="emptyListReasoning"
        item-key="server_id.server_code"
        :loader-height="2"
        @update:options="findServers"
      >
        <template #[`item.server_id.server_code`]="{ item }">
          <div class="server-code xrd-clickable" @click="toDetails(item)">
            <xrd-icon-base class="mr-4">
              <xrd-icon-security-server />
            </xrd-icon-base>
            {{ item.server_id.server_code }}
          </div>
        </template>
      </v-data-table-server>
    </searchable-titled-view>
  </div>
</template>

<script lang="ts">
/**
 * View for 'security servers' tab
 */
import { defineComponent } from 'vue';
import { RouteName } from '@/global';
import { SecurityServer } from '@/openapi-types';
import { useSecurityServer } from '@/store/modules/security-servers';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { debounce } from '@/util/helpers';
import { VDataTableServer } from 'vuetify/labs/VDataTable';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import { DataQuery, DataTableHeader } from '@/ui-types';
import { XrdIconSecurityServer } from '@niis/shared-ui';
import SearchableTitledView from '@/components/ui/SearchableTitledView.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: { SearchableTitledView, XrdIconSecurityServer, VDataTableServer },
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
      ];
    },
    emptyListReasoning(): string {
      return this.search
        ? this.$t('noData.noMatches')
        : this.$t('noData.noSecurityServers');
    },
  },

  watch: {
    search: function () {
      this.debouncedFindServers();
    },
  },
  created() {
    that = this;
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
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
    findServers: async function ({ itemsPerPage, page, sortBy }) {
      this.dataQuery.itemsPerPage = itemsPerPage;
      this.dataQuery.page = page;
      this.dataQuery.sortBy = sortBy[0]?.key;
      this.dataQuery.sortOrder = sortBy[0]?.order;
      this.fetchServers();
    },
    fetchServers: async function () {
      this.loading = true;
      this.dataQuery.search = this.search;
      try {
        await this.securityServerStore.find(this.dataQuery);
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

.server-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
