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
  <v-card class="details-card" flat>
    <v-card-title class="card-title">{{ titleText }}</v-card-title>
    <v-divider></v-divider>
    <v-card-text class="card-content"
      ><div>{{ infoText }}</div>
      <!-- Use action prop & emit for one button. Use "actions" slot if more customisation is needed. -->
      <slot name="actions">
        <xrd-button
          v-if="actionText && showAction"
          text
          :outlined="false"
          class="btn-adjust"
          data-test="info-card-edit-button"
          @click="emitActionClick"
          >{{ actionText }}</xrd-button
        ></slot
      ></v-card-text
    >
    <v-divider class="pb-4"></v-divider>
  </v-card>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  name: 'InfoCard',
  props: {
    // Text for the title
    titleText: {
      type: String,
      required: true,
    },
    // Information text
    infoText: {
      type: String,
      required: true,
    },
    // Action in the right end
    actionText: {
      type: String,
      required: false,
      default: undefined,
    },
    showAction: {
      type: Boolean,
      required: false,
      default: true,
    },
  },
  methods: {
    emitActionClick(): void {
      this.$emit('actionClicked');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-content {
  display: flex;
  justify-content: space-between;
}

/* v-card-text has so much padding that this is needed for the button. Without it the height would not be even. */
.btn-adjust {
  margin-top: -9px;
  margin-bottom: -9px;
}
</style>
