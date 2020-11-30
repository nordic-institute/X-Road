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
  <div class="new-content">
    <div class="cert-dialog-header">
      <span class="identifier-wrap">{{ title }}</span>
      <v-spacer></v-spacer>
      <i v-if="showClose" @click="close()" id="close-x"></i>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    title: {
      type: String,
    },
    showClose: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {};
  },
  methods: {
    close() {
      this.$emit('close');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/functions';

.new-content {
  max-width: 850px;
  width: 100%;
}

.cert-dialog-header {
  display: flex;
  justify-content: center;
  border-bottom: 1px solid $XRoad-Grey40;
  color: $XRoad-Grey60;
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 51px;
}

#close-x {
  cursor: pointer;
  font-style: normal;
  font-size: 50px;
  color: $XRoad-Grey40;
}

#close-x:before {
  content: unicode('00d7');
}
</style>
