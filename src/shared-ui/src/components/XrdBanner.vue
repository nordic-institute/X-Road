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
  <v-banner class="my-4 border border-s-xl" variant="outlined" bg-color="background" rounded :icon="icon" :color="color" :border="color">
    <v-banner-text v-if="text || $slots.default">
      <slot>
        {{ translatedText ? text : $t(text) }}
      </slot>
    </v-banner-text>

    <v-banner-actions v-if="$slots.actions" class="ma-0 ml-auto align-self-center">
      <slot name="actions" />
    </v-banner-actions>
  </v-banner>
</template>

<script lang="ts" setup>
defineProps({
  color: {
    type: String,
    default: 'success',
  },
  icon: {
    type: String,
    default: 'check_circle',
  },
  text: {
    type: String,
    default: '',
  },
  translatedText: {
    type: Boolean,
    default: false,
  },
});
</script>

<style lang="scss" scoped></style>
