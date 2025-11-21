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
    <v-icon :color="color" :icon="icon" />
  </div>
</template>

<script lang="ts" setup>
import { Status, statusToType } from '../types';
import { computed } from 'vue';

/**
 * General purpose component for status icon with color
 */

const props = defineProps<{ status: Status }>();

const color = computed(() => statusToType(props.status));
const icon = computed(() => {
  switch (props.status) {
    case 'ok':
      return 'check_circle__filled';
    case 'ok-disabled':
      return 'check_circle__filled';
    case 'saved':
      return 'check';
    case 'progress-register':
      return 'cached';
    case 'progress-register-disabled':
      return 'cached';
    case 'progress-delete':
      return 'cached';
    case 'error':
      return 'error__filled';
    case 'error-disabled':
      return 'error__filled';
    case 'pending':
      return 'cached';
    case 'pending-disabled':
      return 'error__filled';
    case 'name-set':
      return 'edit_square';
    case 'name-submitted':
      return 'edit_square';
    default:
      return 'error__filled';
  }
});
</script>

<style lang="scss" scoped></style>
