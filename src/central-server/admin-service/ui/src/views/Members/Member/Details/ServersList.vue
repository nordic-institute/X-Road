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
  <XrdCard :title="titleKey" class="bg-surface-container" variant="flat">
    <template #append-title>
      <XrdSearchField
        v-model="search"
        data-test="search-query-field"
        width="360"
        :label="$t('action.search')"
      />
    </template>
    <v-data-table
      data-test="servers-table"
      class="xrd bg-surface-container"
      item-key="id"
      hide-default-footer
      :loading="loading"
      :headers="headers"
      :items="servers"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      :loader-height="2"
    >
      <template #[`item.server_id.server_code`]="{ item }">
        <div
          class="server-code cursor-pointer"
          :data-test="`server-${item.server_id.server_code}`"
          @click="toSecurityServerDetails(item)"
        >
          {{ item.server_id.server_code }}
        </div>
      </template>
      <template #[`item.action`]="{ item }">
        <slot name="actions" :server="item"></slot>
      </template>
    </v-data-table>
  </XrdCard>
</template>
<script setup lang="ts">
import { XrdCard } from '@niis/shared-ui';
import { ref } from 'vue';
import { SecurityServer } from '@/openapi-types';
import { RouteName } from '@/global';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

defineProps({
  servers: {
    type: Array as () => SecurityServer[],
    required: true,
  },
  loading: {
    type: Boolean,
    required: true,
  },
  titleKey: {
    type: String,
    required: true,
  },
});

const { t } = useI18n();
const router = useRouter();

const search = ref('');

const headers = [
  {
    title: t('global.server') as string,
    align: 'start',
    key: 'server_id.server_code',
  },
  {
    title: '',
    align: 'end',
    key: 'action',
  },
] as DataTableHeader[];

function toSecurityServerDetails(securityServer: SecurityServer): void {
  router.push({
    name: RouteName.SecurityServerDetails,
    params: { serverId: securityServer.server_id.encoded_id || '' },
  });
}
</script>
