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
  <XrdLabel
    :color="color ? color : labelColor"
    :bold="bold"
    :semi-bold="semiBold"
    :clickable="clickable"
    :spacing12="spacing12"
    @navigate="emit('navigate')"
  >
    <template #prepend-label>
      <v-icon
        size="24"
        :class="iconColorClass"
        :icon="icon"
      />
    </template>
    <template #label>
      <slot name="label">
        {{ label }}
      </slot>
    </template>
    <template
      v-if="$slots['append-label']"
      #append-label
    >
      <slot name="append-label" />
    </template>
  </XrdLabel>
</template>

<script lang="ts" setup>
import XrdLabel from './XrdLabel.vue';
import { computed } from 'vue';

const props = defineProps({
  label: {
    type: [String, Number],
    default: '',
  },
  labelColor: {
    type: String,
    default: 'primary',
  },
  icon: {
    type: String,
    required: true,
  },
  iconColor: {
    type: String,
    default: 'tertiary',
  },
  color: {
    type: String,
    default: '',
  },
  clickable: {
    type: Boolean,
    default: false,
  },
  semiBold: {
    type: Boolean,
    default: false,
  },
  bold: {
    type: Boolean,
    default: false,
  },
  spacing12: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['navigate']);

const iconColorClass = computed(() => {
  const color = props.color ? props.color : props.iconColor;
  if (!color) {
    return '';
  }

  const prefix = color.startsWith('on-') ? '' : 'text-';

  return prefix + color;
});
</script>

<style lang="scss" scoped></style>
