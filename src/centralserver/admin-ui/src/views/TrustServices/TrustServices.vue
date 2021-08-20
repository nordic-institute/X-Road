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
    <!-- Title and button -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('trustServices.certificationServices') }}
      </div>

      <xrd-button data-test="add-certification-service" @click="() => {}">
        <xrd-icon-base class="xrd-large-button-icon"
          ><XrdIconAdd
        /></xrd-icon-base>
        {{ $t('trustServices.addCertificationService') }}</xrd-button
      >
    </div>

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
      <template #[`item.server`]="{ item }">
        <div class="server-code">
          <xrd-icon-base class="mr-4"><XrdIconCertificate /></xrd-icon-base>
          {{ item.server }}
        </div>
      </template>

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Title and button -->
    <div class="table-toolbar align-fix mt-8 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('trustServices.timestampingServices') }}
      </div>

      <xrd-button data-test="add-timestamping-service" @click="() => {}">
        <xrd-icon-base class="xrd-large-button-icon"
          ><XrdIconAdd
        /></xrd-icon-base>
        {{ $t('trustServices.addTimestampingService') }}</xrd-button
      >
    </div>

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
      <template #[`item.server`]="{ item }">
        <div class="server-code">
          <xrd-icon-base class="mr-4"><XrdIconCertificate /></xrd-icon-base
          >{{ item.server }}
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
 * View for 'trust services' tab
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
          server: 'X-Road test CA CN',
          validFrom: '2021-01-15',
          validTo: '2024-03-13',
        },
        {
          server: 'X-Road test CA CN 2',
          validFrom: '2021-03-10',
          validTo: '2025-03-12',
        },
      ],
      timestampingServices: [
        {
          server: 'X-Road test CA CN',
          validFrom: '2021-01-15',
          validTo: '2024-03-13',
        },
        {
          server: 'X-Road test CA CN 2',
          validFrom: '2021-03-10',
          validTo: '2025-03-12',
        },
      ],
    };
  },
  computed: {
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('trustServices.approvedCertificationService') as string,
          align: 'start',
          value: 'server',
          class: 'xrd-table-header ts-table-header-server-code',
        },
        {
          text: this.$t('trustServices.validFrom') as string,
          align: 'start',
          value: 'validFrom',
          class: 'xrd-table-header ts-table-header-valid-from',
        },
        {
          text: this.$t('trustServices.validTo') as string,
          align: 'start',
          value: 'validTo',
          class: 'xrd-table-header ts-table-header-valid-to',
        },
      ];
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
