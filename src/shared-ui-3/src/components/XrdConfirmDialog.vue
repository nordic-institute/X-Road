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
  <xrd-simple-dialog
    :title="title"
    :cancel-button-text="cancelButtonText"
    :save-button-text="acceptButtonText"
    :show-close="false"
    :loading="loading"
    @save="$emit('accept')"
    @cancel="$emit('cancel')"
  >
    <template #text>
        <slot name="text">{{ $t(text, data) }}</slot>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
/**
 * A dialog for simple "accept or cancel" functions
 */

import type { PropType } from 'vue';
import { defineComponent } from "vue";
import XrdSimpleDialog from "./XrdSimpleDialog.vue";

export default defineComponent({
  components: { XrdSimpleDialog },
  props: {
    modelValue: {
      type: Boolean,
      default: false,
    },
    title: {
      type: String,
      required: true,
    },
    text: {
      type: String,
      default: '',
    },
    cancelButtonText: {
      type: String,
      default: 'action.cancel',
    },
    acceptButtonText: {
      type: String,
      default: 'action.yes',
    },
    // Set save button loading spinner
    loading: {
      type: Boolean,
      default: false,
    },
    // In case the confirmation text requires additional data
    data: {
      type: Object as PropType<Record<string, unknown>>,
      required: false,
      default: {} as Record<string, unknown>,
    },
  },
  emits: ['cancel', 'accept'],
});
</script>
<style lang="scss" scoped>
</style>
