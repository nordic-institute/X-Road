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
    title="systemSettings.selectSecurityServer.title"
    save-button-text="action.select"
    width="824"
    z-index="1999"
    scrollable
    :disable-save="!selected || !changed"
    @cancel="cancel"
    @save="registerServiceProvider"
  >
    <template #content>
      <div style="height: 500px">
        <v-text-field
          v-model="pagingOptions.search"
          :label="$t('systemSettings.selectSecurityServer.search')"
          single-line
          hide-details
          class="search-input"
          autofocus
          append-inner-icon="icon-Search"
          variant="underlined"
          data-test="management-security-server-search-field"
          @update:model-value="debouncedFetchItems"
        >
        </v-text-field>
        <!-- Table -->
        <v-data-table-server
          v-model="selectedSecurityServers"
          class="elevation-0 xrd-table"
          item-value="server_id.encoded_id"
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
        />
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import {
  ManagementServicesConfiguration,
  SecurityServer,
} from '@/openapi-types';
import { mapActions, mapStores } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { debounce } from '@/util/helpers';
import { DataQuery, DataTableHeader, Event } from '@/ui-types';
import { useSecurityServer } from '@/store/modules/security-servers';
import { TranslateResult } from 'vue-i18n';
import { VDataTableServer } from 'vuetify/labs/VDataTable';
import { defaultItemsPerPageOptions } from '@/util/defaults';
import { useManagementServices } from '@/store/modules/management-services';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default defineComponent({
  components: {
    VDataTableServer,
  },
  props: {
    currentSecurityServer: {
      type: String,
      default: '',
    },
  },
  emits: [Event.Cancel, Event.Select],
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
    emptyListReasoning(): TranslateResult {
      return this.pagingOptions.search
        ? this.$t('noData.noMatches')
        : this.$t('noData.noSecurityServers');
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
    that = this;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
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
        this.showError(error);
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
      this.$emit(Event.Cancel);
    },
    registerServiceProvider(): void {
      this.loading = true;
      this.managementServicesStore
        .registerServiceProvider({
          security_server_id: this.selectedSecurityServers[0] || '',
        })
        .then(() => {
          this.showSuccess(
            this.$t('systemSettings.serviceProvider.registeredSuccess', {
              subsystemId:
                this.managementServicesConfiguration.service_provider_id,
              securityServerId:
                this.managementServicesConfiguration.security_server_id,
            }),
          );
          this.$emit(Event.Select, this.selectedSecurityServers);
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
