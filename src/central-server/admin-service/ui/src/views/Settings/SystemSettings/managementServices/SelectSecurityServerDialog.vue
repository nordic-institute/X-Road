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
  <XrdSimpleDialog
    title="systemSettings.selectSecurityServer.title"
    save-button-text="action.select"
    width="824"
    submittable
    scrollable
    :disable-save="!selected || !changed"
    @cancel="cancel"
    @save="registerServiceProvider"
  >
    <template #content>
      <!-- Table -->
      <v-data-table-server
        v-model="selectedSecurityServers"
        class="xrd border xrd-rounded-12"
        item-value="server_id.encoded_id"
        max-height="420"
        show-select
        select-strategy="single"
        :loading="loading"
        :headers="headers"
        :items="selectableSecurityServers"
        :items-length="
          securityServerStore.securityServerPagingOptions.total_items
        "
        :items-per-page-options="itemsPerPageOptions"
        :items-per-page="pagingOptions.itemsPerPage"
        :page="pagingOptions.page"
        :no-data-text="emptyListReasoning"
        :loader-height="2"
        @update:options="changeOptions"
      >
        <template #top>
          <v-text-field
            v-model="pagingOptions.search"
            data-test="management-security-server-search-field"
            class="xrd w-50 mb-6 ml-4"
            prepend-inner-icon="search"
            single-line
            hide-details
            autofocus
            :label="$t('systemSettings.selectSecurityServer.search')"
            @update:model-value="debouncedFetchItems"
          >
          </v-text-field>
        </template>
        <template #bottom>
          <XrdPagination />
        </template>
      </v-data-table-server>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { mapStores } from 'pinia';

import { XrdSimpleDialog, useNotifications, XrdPagination } from '@niis/shared-ui';

import {
  ManagementServicesConfiguration,
  SecurityServer,
} from '@/openapi-types';
import { useManagementServices } from '@/store/modules/management-services';
import { useSecurityServer } from '@/store/modules/security-servers';
import { DataQuery } from '@/ui-types';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import { debounce } from '@/util/helpers';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: { XrdSimpleDialog, XrdPagination },
  props: {
    currentSecurityServer: {
      type: String,
      default: '',
    },
  },
  emits: ['cancel', 'select'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    const options = defaultItemsPerPageOptions();
    return {
      loading: false,
      itemsPerPageOptions: options,
      pagingOptions: { page: 1, itemsPerPage: options[0].value } as DataQuery,
      selectedSecurityServers: (this.currentSecurityServer
        ? [this.currentSecurityServer?.replace('SERVER:', '')]
        : []) as string[],
    };
  },
  computed: {
    ...mapStores(useSecurityServer, useManagementServices),
    managementServicesConfiguration(): ManagementServicesConfiguration {
      return this.managementServicesStore.managementServicesConfiguration;
    },
    selectableSecurityServers(): SecurityServer[] {
      return this.securityServerStore.securityServers || [];
    },
    emptyListReasoning(): string {
      return this.pagingOptions.search
        ? 'noData.noMatches'
        : 'noData.noSecurityServers';
    },
    changed(): boolean {
      return (
        this.currentSecurityServer?.replace('SERVER:', '') !==
        this.selectedSecurityServers[0]
      );
    },
    selected(): boolean {
      return this.selectedSecurityServers?.length === 1;
    },
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
  },
  created() {
    //eslint-disable-next-line @typescript-eslint/no-this-alias
    that = this;
  },
  methods: {
    debouncedFetchItems: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.pagingOptions.page = 1;
      that.findServers(that.pagingOptions);
    }, 600),
    findServers: async function () {
      this.loading = true;

      try {
        await this.securityServerStore.find(this.pagingOptions);
      } catch (error: unknown) {
        this.addError(error);
      } finally {
        this.loading = false;
      }
    },
    changeOptions: async function ({ itemsPerPage, page, sortBy }) {
      this.pagingOptions.itemsPerPage = itemsPerPage;
      this.pagingOptions.page = page;
      this.pagingOptions.sortBy = sortBy[0]?.key;
      this.pagingOptions.sortOrder = sortBy[0]?.order;
      await this.findServers();
    },
    cancel(): void {
      this.$emit('cancel');
    },
    registerServiceProvider(): void {
      this.loading = true;
      this.managementServicesStore
        .registerServiceProvider({
          security_server_id: this.selectedSecurityServers[0] || '',
        })
        .then(() => {
          this.addSuccessMessage(
            'systemSettings.serviceProvider.registeredSuccess',
            {
              subsystemId:
                this.managementServicesConfiguration.service_provider_id,
              securityServerId:
                this.managementServicesConfiguration.security_server_id,
            },
          );
          this.$emit('select', this.selectedSecurityServers);
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.loading = false));
    },
  },
});
</script>
