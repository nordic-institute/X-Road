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
  <svg
    width="24"
    height="24"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path
      d="M10 7C13.866 7 17 5.88071 17 4.5C17 3.11929 13.866 2 10 2C6.13401 2 3 3.11929 3 4.5C3 5.88071 6.13401 7 10 7Z"
      :stroke="iconColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M17 10C17 11.1067 13.8889 12 10 12C6.11111 12 3 11.1067 3 10"
      :stroke="iconColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M21 8V19.5294C21 20.8965 17.8889 22 14 22C12.0341 22 10.267 21.718 9 21.2625"
      :stroke="iconColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
    <path
      d="M3 4V15.5294C3 16.8965 6.11111 18 10 18C13.8889 18 17 16.8965 17 15.5294V4"
      :stroke="iconColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  name: 'IconBase',
  props: {
    iconName: {
      type: String,
      default: 'box',
    },
    width: {
      type: [Number, String],
      default: 24,
    },
    height: {
      type: [Number, String],
      default: 24,
    },
    iconColor: {
      type: String,
      default: 'currentColor',
    },
  },
  methods: {
    keyClick(): void {
      this.$emit('click');
    },
  },
});
</script>

<style lang="scss" scoped>
svg {
  display: inline-block;
  vertical-align: baseline;
  min-width: 24px;
  min-height: 24px;
  margin-right: 10px;
}
</style>
