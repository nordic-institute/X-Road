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
    <div class="wizard-step-form-content">
      {{ $t('wizard.signKey.info') }}
      <div class="row-wrap">
        <FormLabel :labelText="$t('wizard.signKey.keyLabel')" />
        <v-text-field
          class="form-input"
          type="text"
          v-model="keyLabel"
          data-test="key-label-input"
          outlined
          autofocus
        ></v-text-field>
      </div>
    </div>
    <div class="button-footer">
      <div class="button-group">
        <large-button
          outlined
          @click="cancel"
          :disabled="!disableDone"
          data-test="cancel-button"
          >{{ $t('action.cancel') }}</large-button
        >
      </div>
      <div>
        <large-button
          @click="previous"
          outlined
          class="previous-button"
          data-test="previous-button"
          >{{ $t('action.previous') }}</large-button
        >
        <large-button @click="done" data-test="next-button">{{
          $t('action.next')
        }}</large-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  computed: {
    keyLabel: {
      get(): string {
        return this.$store.getters.keyLabel;
      },
      set(value: string) {
        this.$store.commit('storeKeyLabel', value);
      },
    },
  },
  data() {
    return {
      disableDone: true,
    };
  },
  methods: {
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
      this.$store.dispatch('generateCsr').then(
        () => {
          this.disableDone = false;
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
