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
  <v-dialog :value="dialog" :width="width" persistent>
    <v-card class="xrd-card" data-test="dialog-simple">
      <v-card-title>
        <slot name="title">
          <span data-test="dialog-title">{{ $t(title) }}</span>
        </slot>
        <v-spacer />
        <close-button
          v-if="showClose"
          id="dlg-close-x"
          data-test="dlg-close-x"
          @click="cancel()"
        />
      </v-card-title>
      <v-card-text class="content-wrapper">
        <slot name="content"></slot>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>
        <large-button
          data-test="dialog-cancel-button"
          class="mr-3"
          outlined
          @click="cancel()"
          >{{ $t(cancelButtonText) }}</large-button
        >
        <large-button
          data-test="dialog-save-button"
          :disabled="disableSaveButton"
          :loading="loading"
          @click="save()"
          >{{ $t(saveButtonText) }}</large-button
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
/** Base component for simple dialogs */

import Vue from 'vue';
import CloseButton from '@/components/CloseButton.vue';
import LargeButton from '@/components/LargeButton.vue';

export default Vue.extend({
  components: {
    CloseButton,
    LargeButton,
  },
  props: {
    // Title of the dialog
    title: {
      type: String,
      required: false,
    },
    // Dialog visible / hidden
    dialog: {
      type: Boolean,
      required: true,
    },
    // Disable save button
    disableSave: {
      type: Boolean,
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
  },

  computed: {
    disableSaveButton(): boolean {
      if (this.disableSave !== undefined && this.disableSave !== null) {
        return this.disableSave;
      }

      return false;
    },
  },

  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    save(): void {
      this.$emit('save');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';

.xrd-card {
  .xrd-card-actions {
    background-color: $XRoad-WarmGrey10;
    height: 72px;
    padding-right: 24px;
  }
}

.content-wrapper {
  margin-top: 18px;
}

.dlg-button-margin {
  margin-right: 14px;
}

.close-button {
  margin-left: auto;
  margin-right: 0;
}
</style>
