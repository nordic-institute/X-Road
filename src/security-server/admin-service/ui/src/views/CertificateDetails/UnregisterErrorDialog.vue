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
    v-if="dialog"
    title="warning"
    cancel-button-text="action.cancel"
    save-button-text="action.yes"
    :show-close="false"
    @save="accept"
    @cancel="cancel"
  >
    <template #content>
      <div data-test="dialog-content-text">
        <template v-if="errorResponse">{{
          $t('keys.unregisterError')
        }}</template>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
/**
 * A dialog for handling error in unregister auth certificate
 */

import { defineComponent } from 'vue';

export default defineComponent({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    errorResponse: {
      type: Object,
      required: true,
    },
    // Set save button loading spinner
    loading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['cancel', 'accept'],
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    accept(): void {
      this.$emit('accept');
    },
  },
});
</script>
