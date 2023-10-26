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
    v-if="dialog"
    :model-value="dialog"
    persistent
    :max-width="maxWidth"
  >
    <v-card>
      <v-card-title class="text-h5">{{ $t('services.warning') }}</v-card-title>
      <v-card-text>
        <div v-for="warning in warnings" :key="warning.code">
          <!-- create the localisation key from warning code -->
          <div class="dlg-warning-header">
            {{ $t(`services.warningCode.${warning.code}`) }}
          </div>
          <div v-for="meta in warning.metadata" :key="meta">{{ meta }}</div>
        </div>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <xrd-button color="primary" variant="outlined" @click="cancel()">{{
          $t(cancelButtonText)
        }}</xrd-button>
        <xrd-button
          color="primary"
          variant="outlined"
          :loading="loading"
          data-test="service-url-change-button"
          @click="accept()"
          >{{ $t(acceptButtonText) }}</xrd-button
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
// A dialog for backend warnings
import { defineComponent, PropType } from 'vue';
import { CodeWithDetails } from '@/openapi-types';
import { XrdButton } from '@niis/shared-ui';

export default defineComponent({
  components: { XrdButton },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
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
      default: '850',
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['cancel', 'accept'],
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    accept(): void {
      this.$emit('accept');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/dialogs';
</style>
