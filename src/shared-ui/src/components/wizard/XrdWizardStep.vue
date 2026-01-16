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
  <v-container class="pa-0" fluid>
    <XrdContainer840 class="pt-4 pr-6 pb-6 pl-6">
      <div v-if="$slots.header || $slots['append-header'] || title || subTitle" class="header mb-6 d-flex flex-row">
        <div v-if="$slots.header || title || subTitle">
          <slot name="header">
            <p v-if="title" class="font-weight-medium body-regular">
              {{ $t(title) }}
            </p>
            <p v-if="subTitle" class="body-regular">
              {{ $t(subTitle) }}
            </p>
          </slot>
        </div>
        <v-spacer v-if="$slots['append-header']" />
        <div v-if="$slots['append-header']">
          <slot name="append-header" />
        </div>
      </div>

      <slot />
    </XrdContainer840>
    <v-footer class="wizard-actions pa-4 bg-surface-container-low">
      <slot name="footer" />
    </v-footer>
  </v-container>
</template>

<script lang="ts" setup>
import { XrdContainer840 } from '../../layouts';

defineProps({
  title: {
    type: String,
    default: '',
  },
  subTitle: {
    type: String,
    default: '',
  },
});
</script>

<style lang="scss" scoped></style>
