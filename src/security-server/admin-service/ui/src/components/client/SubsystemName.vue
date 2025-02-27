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
  <span class="subsystem-name" :class="{'undefined-name':hasNoName}" v-tooltip="tooltip">{{ nameToDisplay }}</span>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { i18n } from '@/plugins/i18n';

const props = defineProps({
  name: String,
});

const { t } = i18n.global;

const hasNoName = computed(() => !props.name);
const nameToDisplay = computed(() => hasNoName.value ? t('client.defaultSubsystemName') : props.name);
const tooltip = computed(() => ({ text: t('client.defaultSubsystemNameTooltip'), 'open-delay': 500, 'open-on-hover': hasNoName.value }));
</script>
<style lang="scss">
.undefined-name {
  font-style: italic;
}
</style>
