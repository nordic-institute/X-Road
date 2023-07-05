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
  <div>
    <div class="wizard-step-form-content py-10 mt-10">
      <div class="wizard-row-wrap">
        <xrd-form-label
          :label-text="$t('wizard.signKey.keyLabel')"
          :help-text="$t('wizard.signKey.info')"
        />
        <v-text-field
          v-model="keyLabel"
          class="wizard-form-input"
          type="text"
          data-test="key-label-input"
          outlined
          autofocus
        ></v-text-field>
      </div>
    </div>
    <div class="button-footer">
      <xrd-button
        outlined
        :disabled="!disableDone"
        data-test="cancel-button"
        @click="cancel"
        >{{ $t('action.cancel') }}</xrd-button
      >

      <xrd-button
        outlined
        class="previous-button"
        data-test="previous-button"
        @click="previous"
        >{{ $t('action.previous') }}</xrd-button
      >
      <xrd-button data-test="next-button" @click="done">{{
        $t('action.next')
      }}</xrd-button>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapActions, mapWritableState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useCsrStore } from '@/store/modules/certificateSignRequest';

export default Vue.extend({
  data() {
    return {
      disableDone: true,
    };
  },
  computed: {
    ...mapWritableState(useCsrStore, ['keyLabel']),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useCsrStore, ['requestGenerateCsr']),
    cancel(): void {
      this.$emit('cancel');
    },
    previous(): void {
      this.$emit('previous');
    },
    done(): void {
      this.$emit('done');
    },
    generateCsr(): void {
      this.requestGenerateCsr().then(
        () => {
          this.disableDone = false;
        },
        (error) => {
          this.showError(error);
        },
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
