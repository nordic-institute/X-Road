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
  <sub-view-container>
    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="certificationServices"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.certificationAuthority`]="{ item }">
        <div class="icon-cell">
          <xrd-icon-base class="mr-4"><XrdIconCertificate /></xrd-icon-base>
          {{ item.certificationAuthority }}
        </div>
      </template>

      <template #[`item.button`]>
        <div class="cs-table-actions-wrap">
          <xrd-button text :outlined="false">{{
            $t('action.delete')
          }}</xrd-button>
        </div>
      </template>

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>
  </sub-view-container>
</template>

<script lang="ts">
/**
 * View for 'security server authentication certificates' tab
 */
import Vue from 'vue';
import SubViewContainer from '@/components/layout/SubViewContainer.vue';
import { DataTableHeader } from 'vuetify';

export default Vue.extend({
  components: {
    SubViewContainer,
  },
  data() {
    return {
      search: '' as string,
      loading: false,
      showOnlyPending: false,
      certificationServices: [
        {
          certificationAuthority: 'X-Road test',
          serialNumber: '12',
          subject: '/C=/FI/O=NIIS/CN=xroad-lxd-ss1',
          expires: '2024-04-08',
        },
        {
          certificationAuthority: 'Test CA CN',
          serialNumber: '4',
          subject: '/C=/FI/O=NIIS/CN=xroad-lxd-ss3',
          expires: '2024-03-13',
        },
      ],
    };
  },
  computed: {
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t(
            'securityServers.securityServer.certificationAuthority',
          ) as string,
          align: 'start',
          value: 'certificationAuthority',
          class: 'xrd-table-header',
        },
        {
          text: this.$t(
            'securityServers.securityServer.serialNumber',
          ) as string,
          align: 'start',
          value: 'serialNumber',
          class: 'xrd-table-header',
        },
        {
          text: this.$t('securityServers.securityServer.subject') as string,
          align: 'start',
          value: 'subject',
          class: 'xrd-table-header',
        },
        {
          text: this.$t('securityServers.securityServer.expires') as string,
          align: 'start',
          value: 'expires',
          class: 'xrd-table-header',
        },
        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header',
        },
      ];
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';

.icon-cell {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
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
