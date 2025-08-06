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
  <v-container class="pa-0" fluid tag="article">
    <header v-if="breadcrumbs.length > 0 || $slots['append-header']" class="view-header d-flex flex-row align-center mt-6 mb-8">
      <template v-for="(bc, idx) in breadcrumbs" :key="idx">
        <span class="title-view" :class="{ 'font-weight-bold': !bc.location }">{{ resolveTitle(bc.title) }}</span>
        <v-btn v-if="bc.location" variant="plain" color="primary" icon="arrow_back" :to="bc.location" />
      </template>
      <slot name="append-header" />
    </header>
    <XrdErrorNotifications />
    <slot name="tabs" />
    <slot />
  </v-container>
</template>

<script lang="ts" setup>
import { useI18n } from 'vue-i18n';
import { XrdViewTitle } from '../utils';
import XrdErrorNotifications from '../components/XrdErrorNotifications.vue';
import { useHistory } from '../stores';

const { t } = useI18n();
const { breadcrumbs } = useHistory();

function resolveTitle(titleProvider: XrdViewTitle) {
  if (typeof titleProvider === 'function') {
    return titleProvider();
  } else if (typeof titleProvider === 'string') {
    return t(titleProvider);
  }
  return undefined;
}
</script>

<style lang="scss" scoped>
.view-header {
  height: 48px;
}
</style>
