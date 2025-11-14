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
  <v-btn
    ref="button"
    rounded="xl"
    height="40"
    :color="color"
    :variant="variant"
    :slim="false"
    :prepend-icon="prependIcon"
    :append-icon="appendIcon"
    :disabled="disabled"
    :loading="loading"
    :type="submit ? 'submit' : 'button'"
  >
    <template
      v-if="prependIcon || $slots.prepend"
      #prepend
    >
      <slot name="prepend">
        <v-icon
          v-if="prependIcon"
          :icon="prependIcon"
          :size="prependIconSize"
        />
      </slot>
    </template>
    <template
      v-if="appendIcon"
      #append
    >
      <v-icon
        v-if="appendIcon"
        :icon="appendIcon"
        :size="appendIconSize"
      />
    </template>
    <slot>
      <span
        v-if="text"
        class="body-regular"
        :class="fontWeight"
      >
        {{ translated ? text : $t(text) }}
      </span>
    </slot>
  </v-btn>
</template>

<script lang="ts" setup>
import { computed, onMounted, PropType, useTemplateRef } from 'vue';

import { VBtn } from 'vuetify/components';

import { PropColor } from '../types';

const props = defineProps({
  text: {
    type: String,
    default: undefined,
  },
  translated: {
    type: Boolean,
    default: false,
  },
  prependIcon: {
    type: String,
    default: undefined,
  },
  appendIcon: {
    type: String,
    default: undefined,
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  submit: {
    type: Boolean,
    default: false,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  autofocus: {
    type: Boolean,
    default: false,
  },
  bold: {
    type: Boolean,
    default: false,
  },
  regular: {
    type: Boolean,
    default: false,
  },
  prependIconSize: {
    type: Number,
    default: 20,
  },
  appendIconSize: {
    type: Number,
    default: 20,
  },
  variant: {
    type: String as PropType<'flat' | 'text' | 'outlined' | 'plain'>,
    default: 'flat',
  },
  color: {
    type: String as PropType<PropColor>,
    default: 'secondary',
  },
});

const fontWeight = computed(() => (props.bold ? 'font-weight-bold' : props.regular ? 'font-weight-regular' : 'font-weight-medium'));

const button = useTemplateRef<VBtn>('button');

function focus() {
  if (button.value && button.value.$el) {
    (button.value.$el as HTMLElement).focus({ focusVisible: true });
  }
}

onMounted(() => {
  if (props.autofocus) {
    focus();
  }
});

defineExpose({ focus });
</script>

<style lang="scss" scoped></style>
