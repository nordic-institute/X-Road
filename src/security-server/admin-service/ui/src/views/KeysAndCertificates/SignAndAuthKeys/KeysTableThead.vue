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
  <thead>
    <tr class="target-row">
      <th
        class="title-col cursor-pointer"
        data-test="name-sort"
        :class="{ selected: selectedSort === sortColumn.NAME }"
        @click="setSort(sortColumn.NAME)"
        @mouseover="hoverName = true"
        @mouseleave="hoverName = false"
      >
        <div class="header-title d-flex flex-row align-center">
          <div>{{ $t('general.name') }}</div>
          <SortButton
            :arrow-state="sortDirection"
            :active="hoverName"
            :selected="selectedSort === sortColumn.NAME"
          />
        </div>
      </th>
      <th
        class="id-col cursor-pointer"
        data-test="id-sort"
        :class="{ selected: selectedSort === sortColumn.ID }"
        @click="setSort(sortColumn.ID)"
        @mouseover="hoverId = true"
        @mouseleave="hoverId = false"
      >
        <div class="header-title d-flex flex-row align-center">
          {{ $t('keys.id') }}
          <SortButton
            :arrow-state="sortDirection"
            :active="hoverId"
            :selected="selectedSort === sortColumn.ID"
          />
        </div>
      </th>
      <th
        class="ocsp-col cursor-pointer"
        data-test="ocsp-sort"
        :class="{ selected: selectedSort === sortColumn.OCSP }"
        @click="setSort(sortColumn.OCSP)"
        @mouseover="hoverOcsp = true"
        @mouseleave="hoverOcsp = false"
      >
        <div class="header-title d-flex flex-row align-center">
          {{ $t('keys.ocsp') }}
          <SortButton
            :arrow-state="sortDirection"
            :active="hoverOcsp"
            :selected="selectedSort === sortColumn.OCSP"
          />
        </div>
      </th>
      <th
        class="expiration-col cursor-pointer"
        data-test="expiration-sort d-flex flex-row align-center"
        :class="{ selected: selectedSort === sortColumn.EXPIRATION }"
        @click="setSort(sortColumn.EXPIRATION)"
        @mouseover="hoverExp = true"
        @mouseleave="hoverExp = false"
      >
        <div class="header-title d-flex flex-row align-center">
          {{ $t('keys.expires') }}
          <SortButton
            :arrow-state="sortDirection"
            :active="hoverExp"
            :selected="selectedSort === sortColumn.EXPIRATION"
          />
        </div>
      </th>
      <th
        class="status-col cursor-pointer"
        data-test="status-sort"
        :class="{ selected: selectedSort === sortColumn.STATUS }"
        @click="setSort(sortColumn.STATUS)"
        @mouseover="hoverStatus = true"
        @mouseleave="hoverStatus = false"
      >
        <div class="header-title d-flex flex-row align-center">
          {{ $t('keys.status') }}
          <SortButton
            :arrow-state="sortDirection"
            :active="hoverStatus"
            :selected="selectedSort === sortColumn.STATUS"
          />
        </div>
      </th>
      <th class="renewal-col">
        <div class="header-title">
          {{ $t('keys.renewal') }}
        </div>
      </th>
      <th class="action-col"></th>
    </tr>
  </thead>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import { defineComponent } from 'vue';
import SortButton from './SortButton.vue';
import { KeysSortColumn } from './keyColumnSorting';

export default defineComponent({
  components: {
    SortButton,
  },
  props: {
    sortDirection: {
      type: Boolean,
      required: true,
    },
    selectedSort: {
      type: String,
      required: true,
    },
  },
  emits: ['set-sort'],
  data() {
    return {
      titleSort: false,
      sortColumn: KeysSortColumn,
      hoverName: false,
      hoverId: false,
      hoverOcsp: false,
      hoverExp: false,
      hoverStatus: false,
    };
  },
  methods: {
    setSort(sort: KeysSortColumn): void {
      this.$emit('set-sort', sort);
    },
  },
});
</script>

<style lang="scss" scoped>
.selected div.header-title {
  color: rgb(var(--v-theme-on-surface));
}
</style>
