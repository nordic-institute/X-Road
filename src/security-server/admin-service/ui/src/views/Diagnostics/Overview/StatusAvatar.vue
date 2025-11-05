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
  <v-avatar v-if="status" class="xrd" density="compact" :color="color">
    <XrdStatusIcon :status="status" />
  </v-avatar>
</template>
<script lang="ts" setup>
import { Status } from '@niis/shared-ui';
import { computed } from 'vue';

const props = defineProps<{ status?: Status }>();

const color = computed(() => {
  switch (props.status) {
    case 'ok':
      return 'success-container';
    case 'ok-disabled':
      return 'warning-container';
    case 'saved':
      return 'warning-container';
    case 'progress-register':
      return 'info-container';
    case 'progress-register-disabled':
      return 'error-container';
    case 'progress-delete':
      return 'error-container';
    case 'error':
      return 'error-container';
    case 'error-disabled':
      return 'error-container';
    case 'pending':
      return 'info-container';
    case 'pending-disabled':
      return 'error-container';
    case 'name-set':
      return 'warning-container';
    case 'name-submitted':
      return 'info-container';
  }
  return undefined;
});
</script>
<style lang="scss" scoped></style>
