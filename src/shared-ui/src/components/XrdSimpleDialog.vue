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
    v-model="showDialog"
    class="xrd-rounded-12"
    :width="width"
    :height="height"
    :persistent="canEscape"
    :scrollable="scrollable"
    @update:model-value="modelValueUpdated"
  >
    <v-form @submit.prevent="submit">
      <v-card class="bg-surface-container-lowest xrd-rounded-12" data-test="dialog-simple">
        <template #title>
          <slot name="title">
            <span class="dialog-title font-weight-bold" data-test="dialog-title">{{ title ? $t(title) : translatedTitle }}</span>
          </slot>
        </template>
        <template #append>
          <v-icon
            v-if="!hideClose"
            :disabled="cancelDisabled"
            icon="close"
            data-test="dlg-close-x"
            color="primary"
            size="default"
            @click="cancel"
          />
        </template>
        <div class="alert-slot pl-6 pr-6">
          <XrdErrorNotifications :manager="errorManager" />
        </div>
        <v-card-text v-if="$slots.text" class="mt-0 mb-6 pb-0 xrd-dialog-text" :class="{ 'no-content': !$slots.content }">
          <span class="body-regular">
            <slot name="text" :dialog-handler="handler" />
          </span>
        </v-card-text>
        <v-card-text v-if="$slots.content" :style="contentStyle" class="mt-0 mb-6 pb-0 xrd-dialog-content">
          <slot name="content" :dialog-handler="handler" />
        </v-card-text>
        <v-card-actions class="pa-4 bg-surface-container-low border-t">
          <XrdBtn
            v-if="!hideCancel"
            data-test="dialog-cancel-button"
            class="font-weight-medium"
            variant="text"
            :disabled="cancelDisabled"
            :text="cancelButtonText"
            @click="cancel"
          />
          <v-spacer />
          <slot name="prepend-save-button" :dialog-handler="handler" />
          <XrdBtn
            v-if="!hideSaveButton"
            ref="saveButton"
            data-test="dialog-save-button"
            class="font-weight-medium"
            :disabled="disableSave"
            :loading="loading"
            :submit="submittable"
            :prepend-icon="saveButtonIcon"
            :text="saveButtonText"
            @click="save"
          />
        </v-card-actions>
      </v-card>
    </v-form>
  </v-dialog>
</template>

<script lang="ts" setup>
/** Base component for simple dialogs */

import { computed, onBeforeMount, onMounted, ref, useTemplateRef } from 'vue';

import { useLocalErrorManager } from '../composables';
import { DialogSaveHandler } from '../types';

import XrdBtn from './XrdBtn.vue';
import XrdErrorNotifications from './XrdErrorNotifications.vue';

const props = defineProps({
  // Title of the dialog
  title: {
    type: String,
    default: '',
  },
  translatedTitle: {
    type: String,
    default: '',
  },
  // Is the content scrollable
  scrollable: {
    type: Boolean,
    default: false,
  },
  // Disable save button
  disableSave: {
    type: Boolean,
    default: false,
  },
  //  cancel button
  allowLoadingCancellation: {
    type: Boolean,
    default: false,
  },
  // Hide save button
  hideSaveButton: {
    type: Boolean,
    default: false,
  },
  cancelButtonText: {
    type: String,
    default: 'action.cancel',
  },
  // Text of the save button
  saveButtonText: {
    type: String,
    default: 'action.add',
  },
  saveButtonIcon: {
    type: String,
    default: 'check_circle',
  },
  width: {
    type: [Number, String],
    default: 840,
  },
  height: {
    type: [Number, String],
    default: undefined,
  },
  hideClose: {
    type: Boolean,
    default: false,
  },
  hideCancel: {
    type: Boolean,
    default: false,
  },
  // Set save button loading spinner
  loading: {
    type: Boolean,
    default: false,
  },
  zIndex: {
    type: [Number, String],
    default: 2400,
  },
  submittable: {
    type: Boolean,
    default: false,
  },
  escapable: {
    type: Boolean,
    default: true,
  },
  focusOnSave: {
    type: Boolean,
    default: false,
  },
  maxContentHeight: {
    type: Number,
    default: -1,
  },
});

const emit = defineEmits<{
  cancel: [];
  save: [evt: Event, handler: DialogSaveHandler];
}>();

const errorManager = useLocalErrorManager();
const handler = ref<DialogSaveHandler>({ addError: errorManager.addError });

const cancelDisabled = computed(() => (props.allowLoadingCancellation ? false : props.loading));
const canEscape = computed(() => (props.escapable ? cancelDisabled.value : false));
const contentStyle = computed(() => {
  const style: Record<string, string> = {};
  if (props.maxContentHeight > 0) {
    style['max-height'] = `${props.maxContentHeight}px`;
  }
  return style;
});

function submit(evt: Event) {
  if (props.submittable && !props.disableSave && !props.loading && !props.hideSaveButton) {
    emit('save', evt, handler.value);
  }
}

function save(evt: Event) {
  if (!props.submittable && !props.disableSave && !props.loading && !props.hideSaveButton) {
    emit('save', evt, handler.value);
  }
}

function cancel() {
  if (!cancelDisabled.value) {
    emit('cancel');
    showDialog.value = true;
  }
}

const showDialog = ref(true);

function modelValueUpdated(displayed: boolean) {
  if (!displayed) {
    cancel();
  }
}

const saveButton = useTemplateRef<{ focus: () => void }>('saveButton');

function blur() {
  const activeElement = document.activeElement as HTMLElement | undefined;
  if (activeElement && activeElement.blur) {
    activeElement.blur();
  }
}

defineExpose({
  focusOnSave() {
    if (saveButton.value) {
      blur();
      saveButton.value.focus();
    }
  },
  addError: errorManager.addError,
});

onMounted(() => {
  if (saveButton.value && props.focusOnSave) {
    saveButton.value.focus();
  }
});

onBeforeMount(() => blur());
</script>

<style lang="scss" scoped></style>
