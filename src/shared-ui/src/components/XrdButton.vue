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
    height="40"
    class="large-button"
    rounded
    :class="{ gradient: showGradient }"
    :variant="variant"
    :disabled="disabled"
    :min-width="minWidth"
    :loading="loading"
    :block="block"
    :large="large"
    :color="color"
    :type="submit ? 'submit' : 'button'"
    @click="click"
  >
    <slot />
  </v-btn>
</template>

<script lang="ts" setup>
/**
 * Wrapper for vuetify button with x-road look
 * */
import { computed, onMounted, ref } from 'vue';

const props = defineProps({
  // Button color
  color: {
    type: String,
    default: 'primary',
  },
  // Set button disabled state
  disabled: {
    type: Boolean,
    default: false,
  },
  // Show loading spinner
  loading: {
    type: Boolean,
    default: false,
  },
  // Block buttons extend the full available width
  block: {
    type: Boolean,
    default: false,
  },
  large: {
    type: Boolean,
    default: false,
  },
  minWidth: {
    type: [Number, String],
    default: 90,
  },
  gradient: {
    type: Boolean,
    default: false,
  },
  outlined: {
    type: Boolean,
    default: false,
  },
  text: {
    type: Boolean,
    default: false,
  },
  plain: {
    type: Boolean,
    default: false,
  },
  submit: {
    type: Boolean,
    default: false,
  },
  autofocus: {
    type: Boolean,
    default: false,
  },
});
const emits = defineEmits(['click']);
const showGradient = computed(() => (props.disabled ? false : props.gradient));
const variant = computed(() => {
  if (props.outlined) {
    return 'outlined';
  } else if (props.text) {
    return 'text';
  } else if (props.plain) {
    return 'plain';
  } else {
    return 'flat';
  }
});

function click(event: MouseEvent): void {
  emits('click', event);
}

const button = ref<VBtn>();

function focus() {
  if (button.value && button.value.$el) {
    (button.value.$el as HTMLButtonElement).focus();
  }
}

onMounted(() => {
  if (props.autofocus) {
    focus();
  }
});

defineExpose({ focus });
</script>

<style lang="scss" scoped>
.large-button {
  border-radius: 20px;
  font-weight: 600;
  text-transform: none;
  letter-spacing: normal;
}

.gradient {
  background: linear-gradient(270deg, #663cdc 0%, #cd9dc8 99.58%);
}
</style>
