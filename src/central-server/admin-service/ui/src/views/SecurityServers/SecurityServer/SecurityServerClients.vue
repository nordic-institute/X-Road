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
  <XrdSubView data-test="security-server-clients-view">
    <template #header>
      <div>
        <XrdRoundedSearchField
          v-model="search"
          data-test="search-query-field"
          width="320"
          :label="$t('action.search')"
        />
      </div>
    </template>

    <!-- Table -->
    <v-data-table
      class="xrd bg-surface-container xrd-rounded-12 border"
      item-key="id"
      hide-default-footer
      :loading="loading"
      :headers="headers"
      :items="clients"
      :items-length="clients.length"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      :loader-height="2"
    >
      <template #[`item.member_name`]="{ item }">
        <XrdLabelWithIcon
          semi-bold
          :icon="item.client_id.subsystem_code ? 'id_card' : 'folder filled'"
          :label="item.member_name"
          :clickable="hasPermissionToMemberDetails"
          @navigate="toMemberDetails(item)"
        />
      </template>
    </v-data-table>
  </XrdSubView>
</template>

<script lang="ts">
/**
 * View for 'security server clients' tab
 */
import { defineComponent } from 'vue';
import { Client } from '@/openapi-types';
import { mapState, mapStores } from 'pinia';
import { useClient } from '@/store/modules/clients';
import { Permissions, RouteName } from '@/global';
import { useUser } from '@/store/modules/user';
import { toMemberId } from '@/util/helpers';
import { XrdSubView, XrdLabelWithIcon, useNotifications } from '@niis/shared-ui';
import { useSecurityServer } from '@/store/modules/security-servers';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

export default defineComponent({
  components: { XrdSubView, XrdLabelWithIcon },
  props: {
    serverId: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      search: '',
      loading: false,
      clients: [] as Client[],
    };
  },
  computed: {
    ...mapStores(useClient, useSecurityServer),
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
  watch: {
    serverId: {
      immediate: true,
      handler(serverId) {
        this.loading = true;
        this.clientStore
          .getBySecurityServerId(serverId)
          .then((resp) => (this.clients = resp))
          .catch((error) => this.addError(error))
          .finally(() => (this.loading = false));
      },
    },
  },
  methods: {
    toMemberDetails(client: Client): void {
      this.$router.push({
        name: RouteName.MemberDetails,
        params: {
          memberId: toMemberId(client.client_id),
        },
      });
    },
  },
});
</script>

<style lang="scss" scoped></style>
