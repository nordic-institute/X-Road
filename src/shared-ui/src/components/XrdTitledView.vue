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
    <header class="header-row d-flex mt-5 mb-5 align-center">
      <div class="xrd-header-title">
        <div class="xrd-view-title">
          <slot name="title">{{ titleKey ? $t(titleKey) : title }}</slot>
        </div>
        <slot name="append-title" />
      </div>

      <div class="xrd-header-buttons">
        <slot name="header-buttons" />
      </div>
    </header>
    <xrd-empty-placeholder :loading="loading" :data="data" :no-items-text="$t('noData.noData')" skeleton-type="table-heading" />

    <slot />
  </div>
</template>

<script lang="ts" setup>
defineProps({
  title: {
    type: String,
    default: '',
  },
  loading: {
    type: Boolean,
    default: false,
  },
  titleKey: {
    type: String,
    default: '',
  },
  data: {
    type: [Object, Array],
    default() {
      return { a: 1 };
    },
  },
});
</script>

<style lang="scss" scoped>
.xrd-header-title {
  display: flex;
  align-items: center;
}

header.header-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  min-height: 45px;

  .xrd-header-title {
    margin-left: 0 !important;
    padding-left: 0 !important;
  }

  .xrd-header-buttons {
    margin-right: 0 !important;
    padding-right: 0 !important;
  }
}
</style>
