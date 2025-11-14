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
  <XrdSimpleDialog :title="title" :width="width" hide-cancel save-button-text="keys.gotIt" @save="emit('cancel')" @cancel="emit('cancel')">
    <template #content>
      <slot />
      <div :class="{ 'mt-6': $slots.default }">
        {{ $t(text) }}
      </div>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
/** Component for help dialogs */
import XrdSimpleDialog from './XrdSimpleDialog.vue';

defineProps({
  // Title of the dialog
  title: {
    type: String,
    required: true,
  },
  width: {
    type: Number,
    default: 840,
  },
  // Help text
  text: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(['cancel']);
</script>

<style lang="scss" scoped></style>
