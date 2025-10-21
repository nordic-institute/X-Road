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
  <v-container class="pa-0 mt-4 xrd-rounded-16 xrd-elevation-1 bg-surface-container-lowest border overflow-hidden" fluid>
    <header
      v-if="hasTitle || $slots['append-header']"
      data-test="view-header"
      class="view-header d-flex flex-row align-center pt-6 pr-6 pl-6 mb-4"
    >
      <span v-if="hasTitle" data-test="view-header-title" class="title-view font-weight-bold">
        {{ translatedTitle ? translatedTitle : $t(title) }}
      </span>

      <slot name="append-header" />
      <template v-if="closeable || goBackOnClose">
        <v-spacer />
        <v-btn data-test="close-x" icon="close" variant="text" size="small" color="primary" @click="close" />
      </template>
    </header>
    <v-progress-linear v-if="loading" bg-color="surface-container" height="1" indeterminate />
    <slot name="bellow-header" />
    <div :class="{ 'mb-4': notificationManager.hasErrors() }">
      <XrdErrorNotifications class="pr-2 pl-2" :manager="notificationManager" />
    </div>
    <slot />
  </v-container>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { useNotifications } from '../composables';

import XrdErrorNotifications from '../components/XrdErrorNotifications.vue';

const props = defineProps({
  title: {
    type: String,
    default: undefined,
  },
  translatedTitle: {
    type: String,
    default: undefined,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  closeable: {
    type: Boolean,
    default: false,
  },
  goBackOnClose: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['close']);

const router = useRouter();
const notificationManager = useNotifications();

const hasTitle = computed(() => props.title || props.translatedTitle);

function close() {
  if (props.goBackOnClose) {
    router.back();
  } else {
    emit('close');
  }
}
</script>

<style lang="scss" scoped>
.view-header {
  height: 48px;
}
</style>
