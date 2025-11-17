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
  <v-chip v-if="hasStatus && style" class="xrd" size="small" :class="style.chipCls" :variant="style.variant">
    <template #prepend>
      <v-icon class="status-icon" :class="style.iconCls" :icon="style.icon" />
    </template>

    <span class="font-weight-medium" :class="style.textCls">
      {{ style.text }}
    </span>
  </v-chip>
</template>

<script lang="ts" setup>
import { PropType, computed } from 'vue';
import { ManagementRequestStatus } from '@/openapi-types';
import { useI18n } from 'vue-i18n';

type Variant = 'flat' | 'outlined';
type Style = {
  text: string;
  icon: string;
  iconCls: string;
  chipCls: string;
  textCls: string;
  variant: Variant;
};

const { t } = useI18n();

const props = defineProps({
  status: {
    type: String as PropType<ManagementRequestStatus>,
    default: undefined,
  },
});

const hasStatus = computed(() => !!props.status);
const style = computed(() => {
  if (hasStatus.value) {
    switch (props.status) {
      case ManagementRequestStatus.REVOKED:
        return buildStyle('managementRequests.revoked', 'on-surface', 'cancel__filled', 'on-surface', 'xrd-outlined', 'outlined');
      case ManagementRequestStatus.DECLINED:
        return buildStyle('managementRequests.rejected', 'on-surface', 'cancel__filled', 'on-surface', 'xrd-outlined', 'outlined');
      case ManagementRequestStatus.APPROVED:
        return buildStyle(
          'managementRequests.approved',
          'on-success-container',
          'check_circle__filled',
          'text-success',
          'bg-success-container',
        );
      case ManagementRequestStatus.WAITING:
        return buildStyle('managementRequests.pending', 'on-warning-container', 'warning__filled', 'text-warning', 'bg-warning-container');
      case ManagementRequestStatus.SUBMITTED_FOR_APPROVAL:
        return buildStyle('managementRequests.submitted', 'on-warning-container', 'warning__filled', 'text-warning', 'bg-warning-container');
    }
  }
  return undefined;
});

function buildStyle(textKey: string, textCls: string, icon: string, iconCls: string, chipCls: string, variant: Variant = 'flat'): Style {
  return { text: t(textKey), textCls, icon, iconCls, chipCls, variant };
}
</script>

<style lang="scss" scoped>
.status-icon {
  margin: 0 4px 0 -4px;
}
.xrd-outlined {
  &.v-chip--variant-outlined {
    border: thin solid rgb(var(--v-theme-on-surface-variant));
    opacity: 0.6;
  }
}
</style>
