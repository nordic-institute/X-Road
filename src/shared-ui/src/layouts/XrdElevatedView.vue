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
  <v-container class="pa-0 mt-4 xrd-rounded-16 xrd-elevation-1 bg-surface-container-lowest" fluid tag="article">
    <header v-if="viewTitle || $slots['append-header']" class="view-header d-flex flex-row align-center pt-6 pr-6 pl-6 mb-4">
      <span v-if="viewTitle" class="title-view font-weight-medium">{{ viewTitle }}</span>

      <slot name="append-header" />
      <v-spacer />
      <v-btn icon="close" variant="text" size="small" color="primary" @click="close" />
    </header>
    <v-breadcrumbs v-if="breadcrumbs && breadcrumbs.length > 0" class="pl-6 mb-4" :items="breadcrumbs">
      <template #title="{ item, index }">
        <span
          class="font-weight-medium body-small"
          :class="{ 'text-primary': index < breadcrumbs.length - 1, 'on-surface': index == breadcrumbs.length - 1 }"
        >
          {{ item.title }}
        </span>
      </template>
      <template #divider>
        <span class="on-surface font-weight-medium body-small opacity-20">/</span>
      </template>
    </v-breadcrumbs>
    <XrdErrorNotifications />
    <slot name="tabs" />
    <slot />
  </v-container>
</template>

<script lang="ts" setup>
import { useRoute, useRouter } from 'vue-router';
import { computed, onMounted, onUnmounted, PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { XrdLocation } from '../utils';
import XrdErrorNotifications from '../components/XrdErrorNotifications.vue';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';

const props = defineProps({
  title: {
    type: String,
    default: undefined,
  },
  titleKey: {
    type: String,
    default: undefined,
  },
  closeOnEscape: {
    type: Boolean,
    default: false,
  },
  breadcrumbs: {
    type: Array as PropType<BreadcrumbItem[]>,
    default: () => [],
  },
});
const { t } = useI18n();

const route = useRoute() as XrdLocation;
const router = useRouter();

const viewTitle = computed(() => {
  if (props.title) {
    return props.title;
  } else if (props.titleKey) {
    return t(props.titleKey);
  }

  return resolveTitle(route);
});

function resolveTitle(route: XrdLocation) {
  if (typeof route.meta.title === 'function') {
    return route.meta.title();
  } else if (typeof route.meta.title === 'string') {
    return t(route.meta.title);
  }
  return undefined;
}

function close() {
  router.back();
}

function doCloseOnEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close();
  }
}

onMounted(() => {
  if (props.closeOnEscape) {
    window.addEventListener('keyup', doCloseOnEscape);
  }
});

onUnmounted(() => {
  if (props.closeOnEscape) {
    window.removeEventListener('keyup', doCloseOnEscape);
  }
});
</script>

<style lang="scss" scoped>
.view-header {
  height: 48px;
}
</style>
