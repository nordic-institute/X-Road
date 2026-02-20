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
  <XrdConfirmDialog
    title="services.warning"
    :max-width="maxWidth"
    :loading="loading"
    :accept-button-text="acceptButtonText"
    :cancel-button-text="cancelButtonText"
    @cancel="cancel"
    @accept="accept"
  >
    <template #text>
      <div v-for="warning in warnings" :key="warning.code">
        <!-- create the localisation key from warning code -->
        <div class="dlg-warning-header font-weight-medium">
          {{ $t(`services.warningCode.${warning.code}`) }}
        </div>
        <div v-for="meta in warning.metadata" :key="meta" class="ml-2">
          {{ meta }}
        </div>
      </div>
    </template>
  </XrdConfirmDialog>
</template>

<script lang="ts" setup>
// A dialog for backend warnings
import { PropType } from 'vue';
import { CodeWithDetails } from '@/openapi-types';
import { XrdConfirmDialog } from '@niis/shared-ui';

const props = defineProps({
  warnings: {
    type: Array as PropType<CodeWithDetails[]>,
    required: true,
  },
  cancelButtonText: {
    type: String,
    default: 'action.cancel',
  },
  acceptButtonText: {
    type: String,
    default: 'action.continue',
  },
  maxWidth: {
    type: String,
    default: '840',
  },
  loading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['cancel', 'accept']);

function cancel(): void {
  emit('cancel');
}

function accept(): void {
  emit('accept');
}
</script>

<style lang="scss" scoped></style>
