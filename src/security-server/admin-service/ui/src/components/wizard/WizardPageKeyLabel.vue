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
  <XrdWizardStep sub-title="wizard.signKey.info">
    <XrdFormBlock>
      <XrdFormBlockRow description="wizard.signKey.info" adjust-against-content>
        <v-text-field
          v-model="keyLabel"
          class="xrd"
          data-test="key-label-input"
          :label="$t('wizard.signKey.keyLabel')"
          maxlength="255"
          autofocus
        />
      </XrdFormBlockRow>
    </XrdFormBlock>
    <template #footer>
      <XrdBtn
        data-test="cancel-button"
        variant="text"
        text="action.cancel"
        @click="cancel"
      />
      <v-spacer />
      <XrdBtn
        data-test="next-button"
        append-icon="arrow_forward"
        text="action.next"
        @click="done"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { mapWritableState } from 'pinia';
import { useCsr } from '@/store/modules/certificateSignRequest';
import {
  XrdWizardStep,
  XrdBtn,
  XrdFormBlock,
  XrdFormBlockRow,
} from '@niis/shared-ui';

export default defineComponent({
  components: {
    XrdWizardStep,
    XrdBtn,
    XrdFormBlock,
    XrdFormBlockRow,
  },
  props: {
    tokenType: {
      type: String,
      required: false,
      default: undefined,
    },
  },
  emits: ['cancel', 'done'],
  computed: {
    ...mapWritableState(useCsr, ['keyLabel']),
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

<style lang="scss" scoped></style>
