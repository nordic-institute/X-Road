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
    <div class="header">
      <div>
        <v-btn fab icon small @click="clicked" class="no-hover">
          <v-icon v-if="isOpen" class="button-icon">mdi-chevron-down</v-icon>
          <v-icon v-else class="button-icon">mdi-chevron-right</v-icon>
        </v-btn>
      </div>
      <div class="header-link">
        <slot name="link"></slot>
      </div>

      <v-spacer />
      <div class="action-wrap">
        <slot name="action"></slot>
      </div>
    </div>
    <div v-if="isOpen" class="content-wrap">
      <slot name="content"></slot>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  name: 'expandable',
  components: {},
  props: {
    isOpen: {
      type: Boolean,
      required: true,
    },
  },
  methods: {
    clicked(): void {
      if (this.isOpen) {
        this.$emit('close');
      } else {
        this.$emit('open');
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';

.no-hover:hover:before,
.no-hover:focus:before {
  background-color: transparent;
}

.no-hover {
  margin-left: 3px;
  margin-right: 3px;
}

.header {
  display: flex;
  align-items: center;
  height: 48px;
  border-radius: 4px;
  background-color: $XRoad-Grey10;
  box-shadow: 0 1px 1px 0 rgba(0, 0, 0, 0.2);
}

.action-wrap {
  padding-right: 8px;
}

.content-wrap {
  padding: 10px;
}
</style>
