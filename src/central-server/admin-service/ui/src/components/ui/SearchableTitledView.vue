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
  <titled-view>
    <template #title>
      <slot name="title">{{ titleValue }}</slot>
    </template>
    <template #append-title>
      <xrd-search
        v-model="query"
        class="search-box"
        data-test="search-query-field"
      />
      <slot name="append-search" />
    </template>
    <template #header-buttons>
      <slot name="header-buttons" />
    </template>
    <slot />
  </titled-view>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import TitledView from '@/components/ui/TitledView.vue';

export default defineComponent({
  components: { TitledView },
  props: {
    title: {
      type: String,
      default: '',
    },
    titleKey: {
      type: String,
      default: '',
    },
    modelValue: {
      type: String,
      default: '',
    },
  },
  emits: ['update:model-value'],
  computed: {
    query: {
      get() {
        return this.modelValue;
      },
      set(newValue: string) {
        this.$emit('update:model-value', newValue);
      },
    },
    titleValue() {
      return this.titleKey ? this.$t(this.titleKey) : this.title;
    },
  },
});
</script>

<style lang="scss" scoped>
.search-box {
  margin: 0 0 5px 20px;
}
</style>
