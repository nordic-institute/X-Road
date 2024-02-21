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
    <div class="wizard-step-form-content pt-6">
      <div class="wizard-row-wrap">
        <xrd-form-label
          v-if="tokenType === 'HARDWARE'"
          :label-text="$t('wizard.signKey.keyLabel')"
          :help-text="$t('wizard.signKey.info')"
        />
        <xrd-form-label
          v-else
          :label-text="$t('wizard.signKey.keyLabel')"
          :help-text="$t('wizard.signKey.info')"
        />

        <v-text-field
          v-model="keyLabel"
          class="wizard-form-input"
          type="text"
          variant="outlined"
          data-test="key-label-input"
          maxlength="255"
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

      <xrd-button data-test="next-button" @click="done">{{
        $t('action.next')
      }}</xrd-button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapWritableState } from 'pinia';
import { useCsr } from '@/store/modules/certificateSignRequest';

export default defineComponent({
  props: {
    tokenType: {
      type: String,
      required: false,
      default: undefined,
    },
  },
  emits: ['cancel', 'done'],
  data() {
    return {
      disableDone: true,
    };
  },
  computed: {
    ...mapWritableState(useCsr, ['keyLabel']),
    keyLabelText(): string {
      if (this.$props.tokenType === 'HARDWARE') {
        return 'wizard.signKey.keyLabel';
      } else {
        return 'keys.keyLabelInput';
      }
    },
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.$emit('done');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
