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
  <v-dialog
    :model-value="true"
    class="xrd-confirm-dialog"
    :persistent="persistent"
    :width="maxWidth ? undefined : width"
    :max-width="maxWidth ? maxWidth : undefined"
  >
    <v-card data-test="dialog-simple" class="xrd-rounded-12 bg-surface-container-lowest">
      <v-card-title class="font-weight-bold title-container pa-6">
        {{ $t(title) }}
      </v-card-title>
      <div class="alert-slot pl-6 pr-6">
        <XrdErrorNotifications :manager="errorManager" />
      </div>
      <v-card-text class="pt-0 pr-6 pl-6 pb-2">
        <slot name="text">
          <span class="font-weight-regular body-regular">
            {{ $t(text, data) }}
          </span>
        </slot>
      </v-card-text>
      <v-card-actions class="pa-4">
        <XrdBtn v-if="!hideCancelButton" data-test="dialog-cancel-button" variant="text" :text="cancelButtonText" @click="emit('cancel')" />
        <XrdBtn
          ref="acceptButton"
          data-test="dialog-save-button"
          class="ml-2"
          variant="text"
          :text="acceptButtonText"
          :loading="loading"
          :autofocus="focusOnAccept"
          @click="accept"
        />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
/**
 * A dialog for simple "accept or cancel" functions
 */

import { PropType, onMounted, onBeforeMount, useTemplateRef } from 'vue';

import XrdBtn from './XrdBtn.vue';
import XrdErrorNotifications from './XrdErrorNotifications.vue';
import { useLocalErrorManager } from '../composables';
import { AddError, DialogSaveHandler } from '../types';

const props = defineProps({
  title: {
    type: String,
    required: true,
  },
  text: {
    type: String,
    default: '',
  },
  cancelButtonText: {
    type: String,
    default: 'action.cancel',
  },
  acceptButtonText: {
    type: String,
    default: 'action.yes',
  },
  width: {
    type: [Number, String],
    default: 400,
  },
  maxWidth: {
    type: [Number, String],
    default: 0,
  },
  // Set save button loading spinner
  loading: {
    type: Boolean,
    default: false,
  },
  // In case the confirmation text requires additional data
  data: {
    type: Object as PropType<Record<string, unknown>>,
    required: false,
    default: {} as Record<string, unknown>,
  },
  focusOnAccept: {
    type: Boolean,
    default: false,
  },
  hideCancelButton: {
    type: Boolean,
    default: false,
  },
  persistent: {
    type: Boolean,
    default: true,
  },
});

const emit = defineEmits<{
  cancel: [];
  accept: [handler: DialogSaveHandler];
}>();

const acceptButton = useTemplateRef<{ focus: () => void }>('acceptButton');

const errorManager = useLocalErrorManager();
const handler = { addError: errorManager.addError } as DialogSaveHandler;

function blur() {
  const activeElement = document.activeElement as HTMLElement | undefined;
  if (activeElement && activeElement.blur) {
    activeElement.blur();
  }
}

function accept() {
  emit('accept', handler);
}

defineExpose({
  focusOnSave() {
    if (acceptButton.value) {
      blur();
      acceptButton.value.focus();
    }
  },
  addError: errorManager.addError,
});

onMounted(() => {
  if (acceptButton.value && props.focusOnAccept) {
    acceptButton.value.focus();
  }
});

onBeforeMount(() => blur());
</script>
<style lang="scss" scoped></style>
