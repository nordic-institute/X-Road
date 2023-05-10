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
    <!-- Toolbar buttons -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-title-search align-fix mt-0 pt-0">
        <div class="xrd-view-title align-fix">
          {{ $t('tab.main.securityServers') }}
        </div>
        <xrd-search v-model="search" class="margin-fix" />
      </div>
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="securityServerStore.securityServers"
      :search="search"
      :must-sort="true"
      :items-per-page="10"
      :options.sync="pagingSortingOptions"
      :server-items-length="
        securityServerStore.securityServerPagingOptions.total_items
      "
      disable-filtering
      class="elevation-0 data-table"
      :no-data-text="emptyListReasoning"
      item-key="server_id.server_code"
      :loader-height="2"
      :footer-props="{ itemsPerPageOptions: [10, 25] }"
      @update:options="findServers"
    >
      <template #[`item.server_id.server_code`]="{ item }">
        <div class="server-code xrd-clickable" @click="toDetails(item)">
          <xrd-icon-base class="mr-4">
            <XrdIconSecurityServer />
          </xrd-icon-base>
          {{ item.server_id.server_code }}
        </div>
      </template>
    </v-data-table>
  </div>
</template>

<script lang="ts">
/**
 * View for 'security servers' tab
 */
import Vue from 'vue';
import { DataOptions, DataTableHeader } from 'vuetify';
import { RouteName } from '@/global';
import { SecurityServer } from '@/openapi-types';
import { useSecurityServerStore } from '@/store/modules/security-servers';
import { mapActions, mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import VueI18n from 'vue-i18n';
import TranslateResult = VueI18n.TranslateResult;
import { debounce } from '@/util/helpers';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default Vue.extend({
  data() {
    return {
      search: '',
      loading: false,
      showOnlyPending: false,
      pagingSortingOptions: {} as DataOptions,
    };
  },
  computed: {
    ...mapStores(useSecurityServerStore),
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('securityServers.serverCode') as string,
          align: 'start',
          value: 'server_id.server_code',
          class: 'xrd-table-header ss-table-header-sercer-code',
        },
        {
          text: this.$t('securityServers.ownerName') as string,
          align: 'start',
          value: 'owner_name',
          class: 'xrd-table-header ss-table-header-owner-name',
        },
        {
          text: this.$t('securityServers.ownerCode') as string,
          align: 'start',
          value: 'server_id.member_code',
          class: 'xrd-table-header ss-table-header-owner-code',
        },
        {
          text: this.$t('securityServers.ownerClass') as string,
          align: 'start',
          value: 'server_id.member_class',
          class: 'xrd-table-header ss-table-header-owner-class',
        },
      ];
    },
    emptyListReasoning(): TranslateResult {
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
    ...mapActions(notificationsStore, ['showError']),
    debouncedFindServers: debounce(() => {
      // Debounce is used to reduce unnecessary api calls
      that.findServers(that.pagingSortingOptions);
    }, 600),
    toDetails(securityServer: SecurityServer): void {
      this.$router.push({
        name: RouteName.SecurityServerDetails,
        params: { serverId: securityServer.server_id.encoded_id || '' },
      });
    },
    changeOptions: async function () {
      this.findServers(this.pagingSortingOptions);
    },
    findServers: async function (options: DataOptions) {
      this.loading = true;

      try {
        await this.securityServerStore.find(options, this.search);
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
@import '~styles/tables';

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
