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
  <v-chip
    class="xrd"
    :variant="type === 'inactive' ? 'outlined' : 'flat'"
    density="compact"
    :color="chipStyle.bgColor"
    :class="chipStyle.chipClass"
  >
    <template #prepend>
      <slot name="icon" :icon="chipStyle.icon" :color="chipStyle.iconColor">
        <v-icon class="mr-1 ml-n1" :icon="chipStyle.icon" :color="chipStyle.iconColor" />
      </slot>
    </template>
    <slot name="text">
      <span class="font-weight-medium body-small">{{ text ? $t(text) : translatedText }}</span>
    </slot>
  </v-chip>
</template>

<script lang="ts" setup>
import { computed, PropType } from 'vue';
import { StatusType } from '../types';

type Chip = { icon: string; bgColor?: string; iconColor?: string; chipClass?: string; textClass?: string };
const props = defineProps({
  type: {
    type: String as PropType<StatusType | 'inactive'>,
    required: true,
  },
  text: {
    type: String,
    default: '',
  },
  translatedText: {
    type: String,
    default: '',
  },
  icon: {
    type: String,
    default: '',
  },
});

const chipStyle = computed<Chip>(() => {
  switch (props.type) {
    case 'error':
      return {
        iconColor: 'error',
        bgColor: 'error-container',
        icon: 'error filled',
      };
    case 'success':
      return {
        iconColor: 'success',
        bgColor: 'success-container',
        icon: 'check_circle filled',
      };
    case 'warning':
      return {
        iconColor: 'warning',
        bgColor: 'warning-container',
        icon: 'warning filled',
      };
    case 'info':
      return {
        iconColor: 'info',
        bgColor: 'info-container',
        icon: 'error filled',
      };
    default:
      return {
        icon: 'cancel',
        chipClass: 'inactive on-surface opacity-60',
      };
  }
});
</script>

<style lang="scss" scoped>
.inactive {
  border-color: rgb(var(--v-theme-on-surface-variant));
}
</style>
