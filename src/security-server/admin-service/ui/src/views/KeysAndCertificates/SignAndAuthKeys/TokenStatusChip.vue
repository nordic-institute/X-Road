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
    v-if="style"
    class="xrd"
    density="compact"
    :class="style.chipClass"
    :color="style.bgColor"
    :variant="style.variant"
  >
    <template #prepend>
      <v-icon
        class="mr-1 ml-n1"
        :icon="style.icon"
        :color="style.iconColor"
        :class="style.textClass"
      />
    </template>
    <span data-test="token-status-text" class="font-weight-medium body-small" :class="style.textClass">
      {{ $t(style.textKey) }}
    </span>
  </v-chip>
</template>

<script lang="ts" setup>
import {
  getTokenUIStatus,
  TokenUIStatus,
} from '@/views/KeysAndCertificates/SignAndAuthKeys/TokenStatusHelper';
import { computed, PropType } from 'vue';
import { TokenStatus } from '@/openapi-types';

type Style = {
  icon: string;
  iconColor?: string;
  textKey: string;
  variant: 'flat' | 'outlined';
  bgColor?: string;
  chipClass?: string;
  textClass?: string;
};

const props = defineProps({
  tokenStatus: {
    type: String as PropType<TokenStatus>,
    required: true,
  },
});

const uiStatus = computed(() => getTokenUIStatus(props.tokenStatus));

const style = computed<Style | undefined>(() => {
  switch (uiStatus.value) {
    case TokenUIStatus.Inactive:
      return {
        icon: 'cancel',
        textKey: 'keys.tokenStatus.inactive',
        variant: 'outlined',
        chipClass: 'opacity-60',
        textClass: 'on-surface',
        bgColor: 'primary',
      };
    case TokenUIStatus.Unavailable:
      return {
        icon: 'block',
        iconColor: 'error',
        textKey: 'keys.tokenStatus.unavailable',
        variant: 'flat',
        bgColor: 'error-container',
      };
    case TokenUIStatus.Unsaved:
      return {
        icon: 'error',
        iconColor: 'error',
        textKey: 'keys.tokenStatus.unsaved',
        variant: 'flat',
        bgColor: 'error-container',
      };
    default:
      return undefined;
  }
});
</script>

<style lang="scss" scoped></style>
