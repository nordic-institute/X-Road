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
  <tr>
    <td class="pl-8">
      <div class="name-wrap-top">
        <i class="icon-Key key-icon" />
        <div class="clickable-link identifier-wrap" @click="keyClick">
          <span v-if="!tokenKey.name || tokenKey.name === ''">{{
            tokenKey.id
          }}</span>
          <span v-else>{{ tokenKey.name }}</span>
        </div>
      </div>
    </td>
    <td class="td-align-right"></td>
  </tr>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import { Prop } from 'vue/types/options';
import { Key } from '@/mock-openapi-types';
export default Vue.extend({
  props: {
    tokenKey: {
      type: Object as Prop<Key>,
      required: true,
    },
    tokenLoggedIn: {
      type: Boolean,
    },
  },
  computed: {
    showGenerateCsr(): boolean {
      // TODO
      return true;
    },

    disableGenerateCsr(): boolean {
      // TODO
      return false;
    },
  },
  methods: {
    keyClick(): void {
      this.$emit('key-click');
    },
    generateCsr(): void {
      this.$emit('generate-csr');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

.td-align-right {
  text-align: right;
}

.clickable-link {
  color: $XRoad-Purple100;
  cursor: pointer;
}

.key-icon {
  margin-right: 18px;
  color: $XRoad-Purple100;
}

.name-wrap-top {
  display: flex;
  flex-direction: row;
  align-items: center;
  align-content: center;
  margin-top: 5px;
  margin-bottom: 5px;
}
</style>
