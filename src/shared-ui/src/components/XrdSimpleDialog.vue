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
    :width="width"
    :persistent="true"
    :scrollable="scrollable"
    :z-index="zIndex"
    class="xrd-dialog-simple"
  >
<!-- TODO vue3 should work just fine without z-index (AddMemberDialog)   -->
    <v-card class="xrd-card " data-test="dialog-simple">
      <template #title>
        <slot name="title">
          <span class="dialog-title" data-test="dialog-title">{{ $t(title) }}</span>
        </slot>
      </template>
      <template #append>
        <v-icon
          v-if="showClose" icon="mdi-close"
          data-test="dlg-close-x"
          color="primary"
          size="default"
          @click="$emit('cancel')"
        />
      </template>
      <v-progress-linear
        v-if="showProgressBar"
        height="10"
        :indeterminate="true"
      />
      <div class="alert-slot">
        <slot name="alert" />
      </div>
      <v-card-text v-if="hasText" class="content-wrapper xrd-card-text" :class="{'no-content': !hasContent }">
        <slot name="text" />
      </v-card-text>
      <v-card-item v-if="hasContent" class="content-wrapper xrd-card-content">
        <slot name="content" />
      </v-card-item>
      <v-card-actions class="xrd-card-actions">
        <v-spacer />
        <xrd-button
          data-test="dialog-cancel-button"
          class="mr-3"
          variant="outlined"
          :disabled="cancelDisabled"
          @click="$emit('cancel')"
        >
          {{ $t(cancelButtonText) }}
        </xrd-button>
        <xrd-button
          v-if="!hideSaveButton"
          data-test="dialog-save-button"
          :disabled="saveDisabled"
          :loading="loading"
          @click="$emit('save')"
        >
          {{ $t(saveButtonText) }}
        </xrd-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">/** Base component for simple dialogs */

import { defineComponent } from "vue";
import XrdButton from "./XrdButton.vue";

export default defineComponent({
  components: { XrdButton },
  props: {
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
    width: {
      type: [Number, String],
      default: 620,
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
    zIndex:{
      type: [Number, String],
      default: 2400
    }
  },
  emits: ['cancel', 'save'],
  data() {
    return {
      showDialog: true
    }
  },
  computed: {
    hasText() {
      return !!this.$slots['text']
    },
    hasContent() {
      return !!this.$slots['content']
    },
    saveDisabled() {
      if (this.disableSave !== undefined && this.disableSave !== null) {
        return this.disableSave;
      }

      return false;
    },
    cancelDisabled() {
      return this.allowLoadingCancellation ? false : this.loading
    }
  }
});

</script>

<style lang="scss" scoped>
@import '../assets/colors';

.xrd-dialog-simple {
  .xrd-card {
    .xrd-card-actions {
      background-color: $XRoad-WarmGrey10;
      height: 72px;
      padding-right: 24px;
    }

    .dialog-title {
      font-size: 20px;
      font-weight: 500;
      letter-spacing: normal;
    }

    .v-card-text.xrd-card-text {
      font-size: 14px;
      letter-spacing: normal;
      color: rgba(0, 0, 0, .6);
      padding: 16px 24px 8px;
      &.no-content {
        padding-bottom: 16px;
      }
    }

    .v-card-item.xrd-card-content {
      padding: 0 24px 0;

      :deep(.v-card-item__content) {
        padding-top: 16px;
        padding-bottom: 16px;
      }
    }
  }
}
.content-wrapper {
}

.dlg-button-margin {
  margin-right: 14px;
}

.close-button {
  margin-left: auto;
  margin-right: 0;
}

.alert-slot {
  margin-left: 20px;
  margin-right: 20px;
}
</style>
