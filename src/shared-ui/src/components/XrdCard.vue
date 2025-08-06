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
  <v-card variant="flat" class="xrd-rounded-12 border" :class="classes">
    <v-card-title
      v-if="title || $slots['append-title'] || $slots['title-actions']"
      class="d-flex flex-row align-center pt-2 pl-4 pb-6 pr-0"
    >
      <div v-if="title" class="font-weight-medium title-component">
        {{ translated ? title : $t(title) }}
      </div>
      <div v-if="$slots['append-title']" class="ml-6">
        <slot name="append-title" />
      </div>
      <div v-if="$slots['title-actions']" class="ml-auto">
        <slot name="title-actions" />
      </div>
    </v-card-title>
    <v-card-text class="pa-0">
      <slot />
    </v-card-text>
  </v-card>
</template>

<script lang="ts" setup>
import { PropType, computed } from 'vue';

const props = defineProps({
  variant: {
    type: String as PropType<'flat'>,
    default: 'flat',
  },
  title: {
    type: String,
    default: undefined,
  },
  bgColor: {
    type: String,
    default: 'surface-container',
  },
  translated: {
    type: Boolean,
    default: false,
  }
});

const classes = computed(() => ['bg-' + props.bgColor]);

</script>

<style lang="scss" scoped></style>
