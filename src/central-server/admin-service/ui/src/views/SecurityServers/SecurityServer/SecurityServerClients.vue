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
  <div data-test="security-server-clients-view">
    <div id="clients-filter">
      <xrd-search v-model="search" class="mb-4" />
    </div>
    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="clients"
      :items-length="clients.length"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
    >
      <template #[`item.member_name`]="{ item }">
        <div
          v-if="hasPermissionToMemberDetails"
          class="table-cell-member-name-action"
          @click="toMemberDetails(item)"
        >
          <xrd-icon-base class="xrd-clickable mr-4">
            <xrd-icon-folder-outline
          /></xrd-icon-base>
          {{ item.member_name }}
        </div>

        <div v-else class="table-cell-member-name">
          <xrd-icon-base class="mr-4">
            <xrd-icon-folder-outline />
          </xrd-icon-base>
          {{ item.member_name }}
        </div>
      </template>

      <template #bottom>
        <div class="cs-table-custom-footer"></div>
      </template>
    </v-data-table>
  </div>
</template>

<script lang="ts">
/**
 * View for 'security server clients' tab
 */
import { defineComponent } from 'vue';
import { Client } from '@/openapi-types';
import { mapActions, mapState, mapStores } from 'pinia';
import { useClient } from '@/store/modules/clients';
import { useNotifications } from '@/store/modules/notifications';
import { Permissions, RouteName } from '@/global';
import { useUser } from '@/store/modules/user';
import { toMemberId } from '@/util/helpers';
import { VDataTable } from 'vuetify/labs/VDataTable';
import { DataTableHeader } from '@/ui-types';

export default defineComponent({
  components: { VDataTable },
  props: {
    serverId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      search: '',
      loading: false,
      clients: [] as Client[],
    };
  },
  computed: {
    ...mapStores(useClient),
    ...mapState(useUser, ['hasPermission']),
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('global.memberName') as string,
          align: 'start',
          key: 'member_name',
        },
        {
          title: this.$t('global.class') as string,
          align: 'start',
          key: 'client_id.member_class',
        },
        {
          title: this.$t('global.code') as string,
          align: 'start',
          key: 'client_id.member_code',
        },
        {
          title: this.$t('global.subsystem') as string,
          align: 'start',
          key: 'client_id.subsystem_code',
        },
      ];
    },
    hasPermissionToMemberDetails(): boolean {
      return this.hasPermission(Permissions.VIEW_MEMBER_DETAILS);
    },
  },
  created() {
    this.loading = true;
    this.clientStore
      .getBySecurityServerId(this.serverId)
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
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    toMemberDetails(client: Client): void {
      this.$router.push({
        name: RouteName.MemberDetails,
        params: {
          memberid: toMemberId(client.client_id),
        },
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

.table-cell-member-name-action {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
}

.table-cell-member-name {
  font-weight: 600;
  font-size: 14px;
}

#clients-filter {
  display: flex;
  justify-content: space-between;
}
</style>
