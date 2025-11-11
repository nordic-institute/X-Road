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
    <v-progress-linear class="xrd" rounded="pill" :active="loading" indeterminate height="2" />
    <slot name="prepend-header" />
    <header
      v-if="title || breadcrumbs.length > 0 || $slots['append-header'] || $slots.title"
      class="view-header d-flex flex-row align-center mt-6 mb-8"
    >
      <template v-for="(bc, idx) in breadcrumbs" :key="idx">
        <span class="title-view">{{ bc.translatedTitle ? bc.title : $t(bc.title) }}</span>
        <v-btn data-test="navigation-back" variant="plain" color="primary" icon="arrow_back" :to="bc.to" />
      </template>
      <slot name="title">
        <span class="title-view font-weight-bold">{{ translatedTitle ? title : $t(title) }}</span>
      </slot>
      <span v-if="titleDetails" class="title-view font-weight-regular opacity-60">({{ $t(titleDetails) }})</span>
      <slot name="append-header" />
    </header>
    <div v-if="$slots.tabs" :class="{ 'mb-4': manager.hasErrors(), 'mb-6': !manager.hasErrors() }">
      <slot name="tabs" />
    </div>
    <div :class="{ 'mb-6': manager.hasErrors() }">
      <XrdErrorNotifications :manager />
    </div>
    <slot />
  </v-container>
</template>

<script lang="ts" setup>
import { PropType } from 'vue';

import { useNotifications } from '../composables';

import XrdErrorNotifications from '../components/XrdErrorNotifications.vue';
import { Breadcrumb } from '../types';

defineProps({
  title: {
    type: String,
    default: '',
  },
  titleDetails: {
    type: String,
    default: '',
  },
  translatedTitle: {
    type: Boolean,
    default: false,
  },
  breadcrumbs: {
    type: Array as PropType<Breadcrumb[]>,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
});

const manager = useNotifications();
</script>

<style lang="scss" scoped>
.view-header {
  height: 48px;
}
</style>
