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
    <header v-if="viewTitle || $slots['append-header']" class="view-header d-flex flex-row align-center mt-6 mb-8">
      <span v-if="parentRoute" class="title-view">{{ parentRouteTitle }}</span>
      <v-btn v-if="parentRoute" variant="plain" icon="arrow_back" :to="parentRoute" />
      <span v-if="fromRoute" class="title-view">{{ fromRouteTitle }}</span>
      <v-btn v-if="fromRoute" variant="plain" icon="arrow_back" :to="fromRoute" />
      <span v-if="viewTitle" class="title-view font-weight-medium">{{ viewTitle }}</span>

      <slot name="append-header" />
    </header>
    <XrdErrorNotifications />
    <slot name="tabs" />
    <slot />
  </v-container>
</template>

<script lang="ts" setup>
import { useRoute, useRouter } from 'vue-router';
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { XrdLocation } from '../utils';
import XrdErrorNotifications from '../components/XrdErrorNotifications.vue';

const props = defineProps({
  title: {
    type: String,
    default: undefined,
  },
  titleKey: {
    type: String,
    default: undefined,
  },
});
const { t } = useI18n();

const router = useRouter();
const route = useRoute() as XrdLocation;

const viewTitle = computed(() => {
  if (props.title) {
    return props.title;
  } else if (props.titleKey) {
    return t(props.titleKey);
  }

  return resolveTitle(route);
});

const fromRoute = computed(() => route.meta.from);
const fromRouteTitle = computed(() => (fromRoute.value ? resolveTitle(fromRoute.value) : undefined));

const listViewName = computed(() => fromRoute.value?.meta.listView || route.meta.listView);

const parentRoute = computed(() => (listViewName.value ? router.resolve({ name: listViewName.value }) : undefined));
const parentRouteTitle = computed(() => (parentRoute.value ? resolveTitle(parentRoute.value) : undefined));

function resolveTitle(route: XrdLocation) {
  if (typeof route.meta.title === 'function') {
    return route.meta.title();
  } else if (typeof route.meta.title === 'string') {
    return t(route.meta.title);
  }
  return undefined;
}
</script>

<style lang="scss" scoped>
.view-header {
  height: 48px;
}
</style>
