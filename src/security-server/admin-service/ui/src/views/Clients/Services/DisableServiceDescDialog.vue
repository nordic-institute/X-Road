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
  <xrd-simple-dialog
    title="services.disableTitle"
    save-button-text="action.ok"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <div class="dlg-edit-row">
        <div class="dlg-row-title">{{ $t('services.disableNotice') }}</div>
        <v-text-field
          v-model="notice"
          single-line
          variant="underlined"
          class="dlg-row-input"
          data-test="disable-notice-text-field"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
// Dialog to confirm service description disabling
import { defineComponent, PropType } from 'vue';
import { ServiceDescription } from '@/openapi-types';

export default defineComponent({
  props: {
    subject: {
      type: Object as PropType<ServiceDescription>,
      required: true,
    },
    subjectIndex: {
      type: Number,
      required: true,
    },
  },
  emits: ['cancel', 'save'],
  data() {
    return {
      notice: '',
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel', this.subject, this.subjectIndex);
      this.clear();
    },
    save(): void {
      this.$emit('save', this.subject, this.subjectIndex, this.notice);
      this.clear();
    },
    clear(): void {
      this.notice = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@use '@/assets/dialogs';
</style>
