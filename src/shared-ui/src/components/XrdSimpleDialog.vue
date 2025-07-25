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
    class="xrd-dialog-simple xrd-rounded-12"
    :width="width"
    :persistent="canEscape"
    :scrollable="scrollable"
    @update:model-value="modelValueUpdated"
  >
    <v-form @submit.prevent="submit">
      <v-card class="xrd-card bg-surface-container-lowest xrd-rounded-12" data-test="dialog-simple">
        <template #title>
          <slot name="title">
            <span class="dialog-title font-weight-bold" data-test="dialog-title">{{ $t(title) }}</span>
          </slot>
        </template>
        <template #append>
          <v-icon v-if="showClose" icon="close" data-test="dlg-close-x" color="primary" size="default" @click="cancel" />
        </template>
        <v-progress-linear v-if="showProgressBar" height="10" :indeterminate="true" />
        <div class="alert-slot">
          <slot name="alert" />
        </div>
        <v-card-text v-if="hasText" class="content-wrapper xrd-card-text" :class="{ 'no-content': !hasContent }">
          <slot name="text" />
        </v-card-text>
        <v-card-item v-if="hasContent" class="content-wrapper xrd-card-content">
          <slot name="content" />
        </v-card-item>
        <v-card-actions class="xrd-card-actions bg-surface-container-low border-t">
          <XrdBtn
            data-test="dialog-cancel-button"
            class="font-weight-medium"
            variant="text"
            :disabled="cancelDisabled"
            :text-key="cancelButtonText"
            @click="cancel"
          />
          <v-spacer />
          <XrdBtn
            v-if="!hideSaveButton"
            ref="saveButton"
            data-test="dialog-save-button"
            class="font-weight-medium"
            :disabled="disableSave"
            :loading="loading"
            :submit="submittable"
            :prepend-icon="saveButtonIcon"
            :text-key="saveButtonText"
            @click="save"
          />
        </v-card-actions>
      </v-card>
    </v-form>
  </v-dialog>
</template>

<script lang="ts" setup>
/** Base component for simple dialogs */

import XrdBtn from './XrdBtn.vue';
import { computed, onBeforeMount, onMounted, ref, useSlots } from 'vue';

const props = defineProps({
  // Title of the dialog
  title: {
    type: String,
    required: true,
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
  showClose: {
    type: Boolean,
    default: true,
  },
  // Set save button loading spinner
  loading: {
    type: Boolean,
    default: false,
  },
  // Show indeterminate progress bar at the top
  showProgressBar: {
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
});

const emits = defineEmits(['cancel', 'save']);

const slots = useSlots();

const hasText = computed(() => !!slots['text']);
const hasContent = computed(() => !!slots['content']);
const cancelDisabled = computed(() => (props.allowLoadingCancellation ? false : props.loading));
const canEscape = computed(() => (props.escapable ? cancelDisabled.value : false));

function submit() {
  if (props.submittable && !props.disableSave && !props.loading && !props.hideSaveButton) {
    emits('save');
  }
}

function save() {
  if (!props.submittable && !props.disableSave && !props.loading && !props.hideSaveButton) {
    emits('save');
  }
}

function cancel() {
  if (!cancelDisabled.value) {
    emits('cancel');
    showDialog.value = true;
  }
}

const showDialog = ref(true);

function modelValueUpdated(displayed: boolean) {
  if (!displayed) {
    cancel();
  }
}

const saveButton = ref<{ focus: () => void }>();

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
});

onMounted(() => {
  if (saveButton.value && props.focusOnSave) {
    saveButton.value.focus();
  }
});

onBeforeMount(() => blur());
</script>

<style lang="scss" scoped>
@use '../assets/colors';

.xrd-dialog-simple {
  .xrd-card {
    .xrd-card-actions {
      background-color: colors.$WarmGrey10;
      height: 72px;
      padding-right: 24px;
      padding-left: 24px;
    }

    .dialog-title {
      font-size: 20px;
      font-weight: 500;
      letter-spacing: normal;
    }

    /* eslint-disable-next-line vue-scoped-css/no-unused-selector */
    .v-card-text.xrd-card-text {
      font-size: 14px;
      letter-spacing: normal;
      color: rgba(0, 0, 0, 0.6);
      padding: 16px 24px 8px;

      &.no-content {
        padding-bottom: 16px;
      }
    }

    /* eslint-disable-next-line vue-scoped-css/no-unused-selector */
    .v-card-item.xrd-card-content {
      padding: 0 24px 0;

      :deep(.v-card-item__content) {
        padding-top: 16px;
        padding-bottom: 16px;
      }
    }
  }
}

.alert-slot {
  margin-left: 20px;
  margin-right: 20px;
}
</style>
