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
    <v-icon
      v-if="status === 'ok'"
      size="small"
      color="#0CC177"
      icon="icon-Checked"
    />
    <v-icon
      v-if="status === 'ok-disabled'"
      size="small"
      color="#575169"
      icon="icon-Checked"
    />
    <v-icon
      v-else-if="status === 'saved'"
      size="x-large"
      color="#211E1E"
      icon="icon-Checkmark"
    />
    <v-icon
      v-else-if="status === 'progress-register'"
      size="small"
      color="#0CC177"
      icon="icon-In-progress"
    />
    <v-icon
      v-else-if="status === 'progress-register-disabled'"
      size="small"
      color="#575169"
      icon="icon-In-progress"
    />
    <v-icon
      v-else-if="status === 'progress-delete'"
      size="small"
      color="#211E1E"
      icon="icon-Cancel"
    />
    <v-icon
      v-else-if="status === 'name-set'"
      size="small"
      color="#211E1E"
      icon="icon-Edit"
    />
    <v-icon
      v-else-if="status === 'name-submitted'"
      size="small"
      color="#0CC177"
      icon="icon-Edit"
    />
    <v-icon
      v-else-if="status === 'error'"
      size="small"
      color="#EC4040"
      icon="icon-Error"
    />
    <v-icon
      v-else-if="status === 'error-disabled'"
      size="small"
      color="#575169"
      icon="icon-Error"
    />
    <v-icon
      v-else-if="status === 'pending'"
      size="small"
      color="#F5A623"
      icon="icon-Error"
    />
    <v-icon
      v-else-if="status === 'pending-disabled'"
      size="small"
      color="#575169"
      icon="icon-Error"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import {
  XrdIconCancel,
  XrdIconChecked,
  XrdIconCheckmark,
  XrdIconError,
  XrdIconInProgress,
} from './icons';

/**
 * General purpose component for status icon with color
 */
export default defineComponent({
  props: {
    status: {
      type: String,
      required: true,
      validator: (val: string) =>
        [
          '',
          'ok',
          'ok-disabled',
          'saved',
          'progress-register',
          'progress-register-disabled',
          'progress-delete',
          'error',
          'error-disabled',
          'pending',
          'pending-disabled',
          'name-set',
          'name-submitted',
        ].includes(val),
    },
  },
  data() {
    return {
      XrdIconError,
      XrdIconChecked,
      XrdIconCheckmark,
      XrdIconInProgress,
      XrdIconCancel,
    };
  },
});
</script>

<style lang="scss" scoped></style>
