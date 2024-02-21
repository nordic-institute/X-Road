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
    v-if="dialog"
    title="services.addWsdl"
    :disable-save="!meta.valid"
    width="620"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <div class="pt-4 dlg-input-width">
        <v-text-field
          v-model="serviceUrl"
          :label="$t('services.url')"
          autofocus
          variant="outlined"
          class="dlg-row-input"
          data-test="service-url-text-field"
          :error-messages="errors"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { useField } from 'vee-validate';

export default defineComponent({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  emits: ['cancel', 'save'],
  setup() {
    const { value, meta, errors, resetField } = useField(
      'serviceUrl',
      {
        required: true,
        wsdlUrl: true,
        max: 255,
      },
      { initialValue: '' },
    );
    return { serviceUrl: value, meta, errors, resetField };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    save(): void {
      this.$emit('save', this.serviceUrl);
      this.clear();
    },
    clear(): void {
      this.resetField();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
