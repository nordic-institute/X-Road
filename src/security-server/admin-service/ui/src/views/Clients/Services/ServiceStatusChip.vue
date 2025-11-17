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
    density="compact"
    :color="chipColor"
    :variant="variant"
    :prepend-icon="icon"
    :class="{
      'opacity-60': !enabled,
      'on-surface': !enabled,
      'disabled-border': !enabled,
    }"
  >
    <template #prepend>
      <v-icon size="16" :color="iconColor" :icon="icon" />
    </template>
    <span class="font-weight-medium body-small">
      {{ $t(enabled ? 'services.status.enabled' : 'services.status.disabled') }}
    </span>
  </v-chip>
</template>

<script lang="ts" setup>
import { computed } from 'vue';

const props = defineProps({
  enabled: {
    type: Boolean,
    required: true,
  },
});

const icon = computed(() => (props.enabled ? 'check_circle filled' : 'cancel'));
const variant = computed(() => (props.enabled ? 'flat' : 'outlined'));
const chipColor = computed(() => (props.enabled ? 'success-container' : undefined));
const iconColor = computed(() => (props.enabled ? 'success' : undefined));
</script>

<style lang="scss" scoped>
.disabled-border {
  border-color: rgb(var(--v-theme-on-surface-variant));
}
</style>
