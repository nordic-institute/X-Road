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
  <article id="anchor" class="mt-5">
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
        <XrdDataTableFooter />
      </template>
    </v-data-table>
  </article>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import DateTime from '@/components/ui/DateTime.vue';
import DataTableToolbar from '@/components/ui/DataTableToolbar.vue';
import { XrdIconCertificate, XrdDataTableFooter } from '@niis/shared-ui';
import { DataTableHeader } from '@/ui-types';

export interface Anchor {
  title: string;
  hash: string;
  createdAt?: string;
}

export default defineComponent({
  components: {
    XrdDataTableFooter,
    DataTableToolbar,
    DateTime,
    XrdIconCertificate,
  },
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
@use '@niis/shared-ui/src/assets/tables' as *;
@use '@niis/shared-ui/src/assets/colors';

.internal-conf-icon {
  margin-right: 15px;
  color: colors.$Purple100;
}
</style>
