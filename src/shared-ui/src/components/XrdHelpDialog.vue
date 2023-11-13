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
  <v-dialog
    v-if="dialog"
    :model-value="dialog"
    :width="width"
    :persistent="true"
  >
    <v-card class="xrd-card">
      <v-card-title>
        <span class="text-h5">{{ $t(title) }}</span>
      </v-card-title>
      <template #append>
        <xrd-close-button @click="$emit('cancel')" />
      </template>
      <v-card-text class="content-wrapper">
        <slot />
        <div class="text-wrap">
          {{ $t(text) }}
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer />
        <xrd-button @click="$emit('cancel')">
          {{ $t('keys.gotIt') }}
        </xrd-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">/** Component for help dialogs */
import { defineComponent } from "vue";
import XrdButton from "./XrdButton.vue";
import XrdCloseButton from "./XrdCloseButton.vue";

export default defineComponent({
  components:{
    XrdButton,
    XrdCloseButton
  },
  props:{
    // Title of the dialog
    title: {
      type: String,
      required: true,
    },
    // Dialog visible / hidden
    dialog: {
      type: Boolean,
      required: true,
    },
    width: {
      type: Number,
      default: 850,
    },
    // Help text
    text: {
      type: String,
      required: true,
    },
  },
  emits:['cancel'],
});
</script>

<style lang="scss" scoped>
@import '../assets/dialogs';

.content-wrapper {
  margin-top: 20px;
}

.title-wrap {
  margin-bottom: 10px;
  width: 100%;
  text-align: center;
}

.text-wrap {
  margin: 10px;
}

.xrd-card {
  .xrd-card-actions {
    background-color: $XRoad-WarmGrey10;
    height: 72px;
    padding-right: 24px;
  }
}
</style>
