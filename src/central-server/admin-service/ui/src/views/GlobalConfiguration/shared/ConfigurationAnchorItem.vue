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
  <XrdCard data-test="anchor" :translated-title="title">
    <template #title-actions>
      <slot />
    </template>
    <v-data-table
      v-if="anchors"
      class="xrd"
      hide-default-footer
      :headers="headers"
      :items="anchors"
      :items-per-page="-1"
      :loading="loading"
      item-key="hash"
    >
      <template #[`item.hash`]="{ item }">
        <XrdLabelWithIcon
          data-test="anchor-hash"
          icon="tag"
          icon-color="tertiary"
          label-color="primary"
          semi-bold
          :label="item.hash"
        />
      </template>
      <template #[`item.createdAt`]="{ item }">
        <XrdDateTime
          data-test="anchor-created-at"
          :value="item.createdAt"
          with-seconds
        />
      </template>
    </v-data-table>
  </XrdCard>
</template>

<script lang="ts" setup>
import { computed, PropType } from 'vue';

import { useI18n } from 'vue-i18n';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { XrdCard, XrdDateTime, XrdLabelWithIcon } from '@niis/shared-ui';

export interface Anchor {
  title: string;
  hash: string;
  createdAt?: string;
}

const props = defineProps({
  loading: {
    type: Boolean,
    default: false,
  },
  anchor: {
    type: Object as PropType<Anchor>,
    default: null,
  },
});

const { t } = useI18n();

const title = computed(() => props.anchor?.title || ''),
  anchors = computed(() => (props.anchor ? [props.anchor] : [])),
  headers = computed(
    () =>
      [
        {
          title: t('globalConf.anchor.certificateHash') as string,
          align: 'start',
          key: 'hash',
        },
        {
          title: t('globalConf.anchor.created') as string,
          align: 'start',
          key: 'createdAt',
        },
      ] as DataTableHeader[],
  );
</script>
