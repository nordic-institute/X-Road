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
    <div class="header-row">
      <div class="xrd-title-search">
        <div class="xrd-view-title">{{ $t('members.header') }}</div>
        <xrd-search v-model="search" />
      </div>
      <xrd-button data-test="add-member-button">
        <xrd-icon-base class="xrd-large-button-icon"
          ><xrd-icon-add
        /></xrd-icon-base>
        {{ $t('members.addMember') }}</xrd-button
      >
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="members"
      :search="search"
      :must-sort="true"
      :items-per-page=10
      :options.sync="options"
      :server-items-length="totalMembers"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      :footer-props="{ itemsPerPageOptions: [10,25]}"
      @update:options="changeOptions"
    >
      <template #[`item.name`]="{ item }">
        <div
          v-if="hasPermissionToMemberDetails"
          class="members-table-cell-name-action"
          @click="toDetails('netum')"
        >
          <xrd-icon-base class="xrd-clickable mr-4"
            ><xrd-icon-folder-outline
          /></xrd-icon-base>

          {{ item.name }}
        </div>

        <div v-else class="members-table-cell-name">
          <xrd-icon-base class="mr-4"
            ><xrd-icon-folder-outline
          /></xrd-icon-base>

          {{ item.name }}
        </div>
      </template>

      <template #footer>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';
import { DataTableHeader } from 'vuetify';
import { userStore } from '@/store/modules/user';
import { mapState } from 'pinia';
import { Permissions } from '@/global';
import { DataOptions } from 'vuetify';
import {Client, MemberClass, PagedClients} from "@/openapi-types";
import * as api from '@/util/api';
import { AxiosRequestConfig } from 'axios';

export default Vue.extend({
  name: 'MemberList',
  data() {
    return {
      search: '',
      loading: false,
      showOnlyPending: false,
      totalMembers: 0,
      options: {} as DataOptions,
      members: [] as Client[] | undefined,
      // pagingStuff - lue mikon esimerkistä miten on toteutettu siinä....oma property vai yksi ja sama
    };
  },
  computed: {
    ...mapState(userStore, ['hasPermission']),
    headers(): DataTableHeader[] {
      return [
        {
          text: (this.$t('global.memberName') as string) + ' (8)',
          align: 'start',
          value: 'member_name',
          class: 'xrd-table-header members-table-header-name',
        },
        {
          text: this.$t('global.memberClass') as string,
          align: 'start',
          value: 'xroad_id.member_class',
          class: 'xrd-table-header members-table-header-class',
        },
        {
          text: this.$t('global.memberCode') as string,
          align: 'start',
          value: 'xroad_id.member_code',
          class: 'xrd-table-header members-table-header-code',
        },
      ];
    },
    hasPermissionToMemberDetails(): boolean {
      return this.hasPermission(Permissions.VIEW_MEMBER_DETAILS);
    },
  },
  created() {
    // this.fetchClients();
  },
  methods: {
    toDetails(): void {
      this.$router.push({
        name: RouteName.MemberDetails,
        params: { memberid: 'netum' },
      });
    },
    changeOptions: async function () {
      this.fetchClients();
    },
    fetchClients(): void {
      this.loading = true;
      const offset = (this.options?.page == null ? 0 : this.options.page - 1);
      const params: any = {
        limit: this.options.itemsPerPage,
        offset: offset,
        sort: this.options.sortBy[0],
        desc: this.options.sortDesc[0]
      };
      const axiosParams: AxiosRequestConfig = { params }

      api
          .get<PagedClients>(`/clients`, axiosParams)
          .then((res) => {
            this.members = res.data.clients;
            this.totalMembers = res.data.paging_metadata.total_items;
            console.log("total members: " + this.totalMembers);
          })
          .catch((error) => {
            // importoi piniasta show error action, kato mallia muualta
            throw "error, handling missing"
          })
          .finally(() => (this.loading = false));
    }
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.members-table-cell-name-action {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

.members-table-cell-name {
  font-weight: 600;
  font-size: 14px;
}
</style>
