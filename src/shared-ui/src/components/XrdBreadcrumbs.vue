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
  <v-breadcrumbs v-if="breadcrumbs && breadcrumbs.length > 0" class="pl-6 pt-0" :items="breadcrumbs">
    <template #title="{ item, index }">
      <span
        class="font-weight-medium body-small"
        :class="{ 'text-primary': index < breadcrumbs.length - 1, 'on-surface': index == breadcrumbs.length - 1 }"
      >
        {{ item.title }}
      </span>
    </template>
    <template #divider>
      <!-- eslint-disable-next-line @intlify/vue-i18n/no-raw-text -->
      <span class="on-surface font-weight-medium body-small opacity-20">/</span>
    </template>
  </v-breadcrumbs>
</template>

<script lang="ts" setup>
import { PropType } from 'vue';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';

defineProps({
  breadcrumbs: {
    type: Array as PropType<BreadcrumbItem[]>,
    default: () => [],
  },
});
</script>

<style lang="scss" scoped></style>
