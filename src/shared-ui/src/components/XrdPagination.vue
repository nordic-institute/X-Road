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
  <div data-test="xrd-paginator" class="xrd-pagination pt-2 pb-2 pr-2">
    <div class="xrd-pagination--label text-right body-small">{{ $t('dataTables.paginator.rowsPerPage') }}</div>
    <div class="xrd-pagination--options mr-6 ml-6 pb-1">
      <v-select
        v-model="perPage"
        data-test="xrd-paginator-select"
        class="per-page-options"
        variant="underlined"
        density="compact"
        max-width="48px"
        hide-details
        :item-title="optionTitle"
        :item-value="(item) => item"
        :items="perPageOptions"
      />
    </div>
    <div class="xrd-pagination--info text-center body-small">{{ $t('dataTables.paginator.info', info) }}</div>
    <div class="xrd-pagination--paginator">
      <v-pagination v-model="page" :length="pageCount" total-visible="0" size="20" />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, PropType, ref, watchEffect } from 'vue';

import { usePagination } from 'vuetify/lib/components/VDataTable/composables/paginate';
import { useI18n } from 'vue-i18n';

const { page, itemsPerPage, pageCount, itemsLength } = usePagination();
const { t } = useI18n();

const props = defineProps({
  perPageOptions: {
    type: Array as PropType<number[]>,
    default: () => [10, 15, 20, -1],
  },
});

const perPage = ref(props.perPageOptions[0]);

watchEffect(() => {
  if (perPage.value < 0) {
    itemsPerPage.value = itemsLength.value;
  } else {
    itemsPerPage.value = perPage.value;
  }
});

const info = computed(() => ({
  total: itemsLength.value,
  start: 1 + (page.value - 1) * itemsPerPage.value,
  end: Math.min(page.value * itemsPerPage.value, itemsLength.value),
}));

function optionTitle(option: number) {
  if (option < 0) {
    return t('dataTables.paginator.allPerPage');
  }
  return option;
}
</script>

<style lang="scss" scoped>
.xrd-pagination {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.xrd-pagination--options {
  :deep(.v-field) {
    font-size: 12px;
  }
}
</style>
