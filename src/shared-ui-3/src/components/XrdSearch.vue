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
  <v-text-field
    v-model="value"
    data-test="search-input"
    class="expanding-search"
    single-line
    density="compact"
    variant="underlined"
    prepend-inner-icon="mdi-magnify"
    hide-details
    :label="label"
    :class="{ closed }"
    @click:prepend-inner="hide=false"
    @focus="hide = false"
    @blur="hide = true"
    @update:model-value="$emit('update:model-value', $event)"
  ></v-text-field>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

/**
 * Wrapper for vuetify button with x-road look
 * */
export default defineComponent({
  props: {
    modelValue: {
      type: String,
      required: true,
    },
    label: {
      type: String,
      default: '',
    },
  },
  emits: ['update:model-value'],
  data() {
    return {
      hide: true,
      value: this.modelValue
    };
  },
  computed: {
    closed() {
      return this.hide && !this.value;
    }
  },
  methods: {}
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';

.expanding-search {
  transition: 0.4s;
  min-width: 220px;
  max-width: 220px;

  :deep(.v-field__input){
    margin-bottom: 0;
  }
}

.closed {
  min-width: 10px;

  :deep(.v-field__outline:before) {
    border-color: $XRoad-WarmGrey30;
  }

  :deep(.v-field__input) {
    width: 5px;
  }

  :deep(.v-field__prepend-inner>i) {
    color: $XRoad-Purple100;
    opacity: 1;
  }
}
</style>
