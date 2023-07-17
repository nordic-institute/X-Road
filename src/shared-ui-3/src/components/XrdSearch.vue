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
    <v-icon v-if="closed" class="xrd-icon-closed" @click="closed = false">
      mdi-magnify
    </v-icon>
    <v-text-field
      v-else
      v-model="value"
      :label="label"
      data-test="search-input"
      single-line
      hide-details
      class="search-input"
      prepend-inner-icon="mdi-magnify"
      clearable
      @update:model-value="$emit('update:model-value', $event)"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

/**
 * Wrapper for vuetify button with x-road look
 * */
defineEmits(['update:model-value']);
const props = defineProps({
  modelValue: {
    type: String,
    required: true,
  },
  label: {
    type: String,
    default: '',
  },
});

let closed = ref(!props.modelValue);
let value = ref(props.modelValue);
</script>

<style lang="scss" scoped>
@import '../assets/colors';

.xrd-icon-closed {
  margin-top: 20px; // adjusted so that icon stays in the same place open/closed
  cursor: pointer;
  color: $XRoad-Purple100;
  margin-left: 12px;
  padding-bottom: 4px; // adjusted so that icon takes same space than input
}
</style>
