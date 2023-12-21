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
  <article id="anchor" class="mt-4">
    <v-data-table
      v-if="anchors"
      class="elevation-0 data-table"
      :headers="headers"
      :items="anchors"
      :items-per-page="-1"
      :loading="loading"
      item-key="hash"
    >
      <template #top>
        <data-table-toolbar :title-value="title">
          <slot />
        </data-table-toolbar>
      </template>
      <template #[`item.hash`]="{ item }">
        <xrd-icon-base class="internal-conf-icon">
          <XrdIconCertificate />
        </xrd-icon-base>
        <span data-test="anchor-hash">{{ item.hash }}</span>
      </template>
      <template #[`item.createdAt`]="{ item }">
        <date-time
          data-test="anchor-created-at"
          :value="item.createdAt"
          with-seconds
        />
      </template>
      <template #bottom>
        <custom-data-table-footer />
      </template>
    </v-data-table>
  </article>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { DataTableHeader } from '@/ui-types';
import { VDataTable } from 'vuetify/labs/VDataTable';
import DateTime from '@/components/ui/DateTime.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';
import DataTableToolbar from '@/components/ui/DataTableToolbar.vue';
import { XrdIconCertificate } from '@niis/shared-ui';

export interface Anchor {
  title: string;
  hash: string;
  createdAt?: string;
}

export default defineComponent({
  components: { CustomDataTableFooter, DataTableToolbar, DateTime, VDataTable, XrdIconCertificate },
  props: {
    loading: {
      type: Boolean,
      default: false,
    },
    anchor: {
      type: Object as PropType<Anchor>,
      default: null,
    },
  },
  data() {
    return {};
  },
  computed: {
    title(): string {
      return this.anchor ? this.anchor.title : '';
    },
    anchors(): Anchor[] {
      return this.anchor ? [this.anchor] : [];
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('globalConf.anchor.certificateHash') as string,
          align: 'start',
          key: 'hash',
        },
        {
          title: this.$t('globalConf.anchor.created') as string,
          align: 'start',
          key: 'createdAt',
        },
      ];
    },
  },

  methods: {},
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';
@import '@/assets/colors';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
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

.internal-conf-icon {
  margin-right: 15px;
  color: $XRoad-Purple100;
}
</style>
