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
    <v-icon @click="closed = false" v-if="closed" class="icon-closed"
      >mdi-magnify</v-icon
    >
    <v-text-field
      v-if="!closed"
      :label="label"
      data-test="search-input"
      single-line
      hide-details
      class="search-input"
      prepend-inner-icon="mdi-magnify"
      clearable
      v-bind:value="value"
      v-on:input="$emit('input', $event)"
    >
    </v-text-field>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
/**
 * Wrapper for vuetify button with x-road look
 * */

export default Vue.extend({
  name: 'xrd-search',
  props: ['value', 'label'],
  computed: {},
  data() {
    return {
      closed: true,
    };
  },
  methods: {
    show(): void {
      this.closed = false;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';

.icon-closed {
  margin-top: 20px; // adjusted so that icon stays in the same place open/closed
  cursor: pointer;
  color: $XRoad-Purple100;
  padding-bottom: 4px; // adjusted so that icon takes same space than input
}
</style>
