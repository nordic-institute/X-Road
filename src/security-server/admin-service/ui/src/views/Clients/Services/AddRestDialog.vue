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
    :width="620"
    title="services.addRest"
    :disable-save="!meta.valid"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <div class="dlg-edit-row">
        <div class="dlg-row-title pb-8">{{ $t('services.serviceType') }}</div>
        <v-radio-group
          v-bind="serviceTypeRef"
          name="serviceType"
          inline
          class="dlg-row-input"
        >
          <v-radio
            data-test="rest-radio-button"
            :label="$t('services.restApiBasePath')"
            value="REST"
          ></v-radio>
          <v-radio
            data-test="openapi3-radio-button"
            :label="$t('services.OpenApi3Description')"
            value="OPENAPI3"
          ></v-radio>
        </v-radio-group>
      </div>

      <div class="pt-3 dlg-input-width">
        <v-text-field
          v-bind="serviceUrlRef"
          :placeholder="$t('services.urlPlaceholder')"
          :label="$t('services.url')"
          data-test="service-url-text-field"
          variant="outlined"
          autofocus
        ></v-text-field>
      </div>

      <div class="pt-3 dlg-input-width">
        <v-text-field
          v-bind="serviceCodeRef"
          variant="outlined"
          data-test="service-code-text-field"
          :label="$t('services.serviceCode')"
          type="text"
          :placeholder="$t('services.serviceCodePlaceholder')"
          :maxlength="255"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { PublicPathState, useForm } from 'vee-validate';

export default defineComponent({
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  emits: ['cancel', 'save'],
  setup() {
    const { meta, resetForm, values, defineComponentBinds } = useForm({
      validationSchema: {
        serviceType: 'required',
        serviceUrl: 'required|max:255|restUrl',
        serviceCode: 'required|max:255|xrdIdentifier',
      },
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const serviceTypeRef = defineComponentBinds('serviceType', componentConfig);
    const serviceUrlRef = defineComponentBinds('serviceUrl', componentConfig);
    const serviceCodeRef = defineComponentBinds('serviceCode', componentConfig);
    return {
      meta,
      resetForm,
      values,
      serviceTypeRef,
      serviceUrlRef,
      serviceCodeRef,
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    save(): void {
      this.$emit(
        'save',
        this.values.serviceType,
        this.values.serviceUrl,
        this.values.serviceCode,
      );
      this.clear();
    },
    clear(): void {
      requestAnimationFrame(() => {
        this.resetForm();
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
