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
  <div class="xrd-table-toolbar d-flex flex-row mb-6">
    <div class="ma-2 pa-2 me-auto align-self-center">
      <slot name="title">
        <div class="xdr-table-title" data-test="view-title">
          {{ titleKey ? $t(titleKey) : titleValue }}
        </div>
      </slot>
    </div>
    <div v-if="$slots.default" class="ma-2 pa-2">
      <slot />
    </div>
    <div v-if="$slots.item2" class="ma-2 pa-2">
      <slot name="item2" />
    </div>
    <div v-if="$slots.item3" class="ma-2 pa-2">
      <slot name="item3" />
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({
  props: {
    titleKey: {
      type: String,
      default: '',
    },
    titleValue: {
      type: String,
      default: '',
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';

div.xrd-table-toolbar {
  margin-bottom: 0 !important;

  .xdr-table-title {
    color: $XRoad-Black100;
    font-style: normal;
    font-weight: bold;
    font-size: 18px;
    line-height: 24px;
  }
}
</style>
