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
  <div class="d-flex flex-row align-center" :class="{ 'cursor-pointer': clickable }" @click="clickable && emit('navigate')">
    <v-icon size="24" :color="iconColor" :icon="icon" />
    <span class="ml-2" :class="textCss">{{ label }}</span>
  </div>
</template>

<script lang="ts" setup>
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
});

const emit = defineEmits(['navigate']);

const labelColorClass = computed(() => {
  if (!props.labelColor) {
    return '';
  }

  const prefix = props.labelColor.startsWith('on-') ? '' : 'text-';

  return prefix + props.labelColor;
});

const textCss = computed(() => ({
  [labelColorClass.value]: !!labelColorClass.value,
  'font-weight-regular': !props.semiBold && !props.bold,
  'font-weight-medium': props.semiBold && !props.bold,
  'font-weight-bold': props.bold,
}));
</script>

<style lang="scss" scoped></style>
