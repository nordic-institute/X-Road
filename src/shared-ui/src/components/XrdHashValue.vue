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
  <span>
    <template v-if="wrapFriendly">
      <template
        v-for="(fragment, idx) in fragments"
        :key="idx"
      >
        <wbr v-if="idx > 0" />
        <span>{{ idx > 0 ? COLON : '' }}{{ fragment }}</span>
      </template>
    </template>
    <template v-else-if="keepRaw">
      {{ value }}
    </template>
    <template v-else>
      {{ fragments?.join(COLON) }}
    </template>
  </span>
</template>

<script lang="ts" setup>
import { computed } from 'vue';

const COLON = ':';

const props = defineProps({
  value: {
    type: String,
    required: true,
  },
  keepRaw: {
    type: Boolean,
    default: false,
  },
  wrapFriendly: {
    type: Boolean,
    default: false,
  },
});

const fragments = computed(() => {
  if (props.value) {
    return props.value.match(/.{1,2}/g) || [];
  }
  return [];
});
</script>
