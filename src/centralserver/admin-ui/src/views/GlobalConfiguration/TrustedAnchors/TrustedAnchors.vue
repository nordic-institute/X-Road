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
    <!-- Internal configuration -->
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">Trusted anchors</div>
      </div>
    </div>

    <!-- Anchor -->
    <div id="anchor" class="mt-4">
      <v-card flat>
        <div class="card-top">
          <div class="card-main-title">Configuration parts</div>
          <div class="card-corner-button pr-4">
            <xrd-button outlined>
              <xrd-icon-base class="xrd-large-button-icon"
                ><XrdIconDownload
              /></xrd-icon-base>
              Download
            </xrd-button>
          </div>
        </div>
        <v-data-table
          :loading="loading"
          :headers="headers"
          :items="trustedAnchors"
          :search="search"
          :must-sort="true"
          :items-per-page="-1"
          class="elevation-0 data-table"
          item-key="id"
          :loader-height="2"
          hide-default-footer
        >
          <template #[`item.hash`]="{ item }">
            <div class="hash-cell">
              <xrd-icon-base class="mr-4 xrd-clickable"
                ><XrdIconCertificate
              /></xrd-icon-base>
              <div>{{ item.hash }}</div>
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
            <div class="cs-table-custom-footer"></div>
          </template>
        </v-data-table>
      </v-card>
    </div>
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';

export default Vue.extend({
  components: {},
  data() {
    return {
      loading: false,
      search: '',
      trustedAnchors: [
        {
          hash: '42:C2:6E:67:BC:07:FE:B8:0E:41:16:2A:97:EF:9F:42:C2:6E:67:BC:07:FE:B8:0E:41:16:2A:97:EF:9F:',
          created: '2021-02-01',
        },
        {
          hash: '22:41:16:2A:97:EF:9F:42:C2:6E:67:BC:07:FE:C2:6E:67:BC:07:FE:B8:0E:B8:0E:41:16:2A:97:EF:7F:',
          created: '2021-05-05',
        },
        {
          hash: '32:C2:6E:67:BC:07:FE:B8:0E:B8:0E:41:16:2A:97:EF:7F:41:16:2A:97:EF:9F:42:C2:6E:67:BC:07:FE:',
          created: '2021-03-12',
        },
      ],
    };
  },
  computed: {
    headers(): DataTableHeader[] {
      return [
        {
          text: 'Certificate HASH (SHA-224)',
          align: 'start',
          value: 'hash',
          class: 'xrd-table-header mr-table-header-id',
        },
        {
          text: this.$t('global.created') as string,
          align: 'start',
          value: 'created',
          class: 'xrd-table-header mr-table-header-created',
        },

        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header mr-table-header-buttons',
        },
      ];
    },
  },

  methods: {},
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-corner-button {
  display: flex;
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}

.hash-cell {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.cert-icon {
  margin-right: 10px;
  color: $XRoad-Purple100;
}

.icon-column-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
}
</style>
