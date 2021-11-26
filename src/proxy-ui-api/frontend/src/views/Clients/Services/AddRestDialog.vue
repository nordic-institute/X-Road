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
  <ValidationObserver ref="form" v-slot="{ valid }">
    <xrd-simple-dialog
      :dialog="dialog"
      :width="620"
      title="services.addRest"
      :disable-save="!valid"
      @save="save"
      @cancel="cancel"
    >
      <div slot="content">
        <div class="dlg-edit-row">
          <div class="dlg-row-title">{{ $t('services.serviceType') }}</div>

          <ValidationProvider
            v-slot="{ errors }"
            rules="required"
            name="serviceType"
            class="validation-provider dlg-row-input"
          >
            <v-radio-group
              v-model="serviceType"
              name="serviceType"
              :error-messages="errors"
              row
            >
              <v-radio
                name="REST"
                :label="$t('services.restApiBasePath')"
                value="REST"
              ></v-radio>
              <v-radio
                name="OPENAPI3"
                :label="$t('services.OpenApi3Description')"
                value="OPENAPI3"
              ></v-radio>
            </v-radio-group>
          </ValidationProvider>
        </div>

        <div class="pt-3 dlg-input-width">
          <ValidationProvider
            v-slot="{ errors }"
            rules="required|restUrl"
            name="serviceUrl"
            class="validation-provider"
          >
            <v-text-field
              v-model="url"
              :placeholder="$t('services.urlPlaceholder')"
              :label="$t('services.url')"
              name="serviceUrl"
              outlined
              autofocus
              :error-messages="errors"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="pt-3 dlg-input-width">
          <ValidationProvider
            v-slot="{ errors }"
            rules="required|xrdIdentifier"
            name="serviceCode"
            class="validation-provider"
          >
            <v-text-field
              v-model="serviceCode"
              outlined
              name="serviceCode"
              :label="$t('services.serviceCode')"
              type="text"
              :placeholder="$t('services.serviceCodePlaceholder')"
              :maxlength="255"
              :error-messages="errors"
            ></v-text-field>
          </ValidationProvider>
        </div>
      </div>
    </xrd-simple-dialog>
  </ValidationObserver>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';

export default Vue.extend({
  components: { ValidationProvider, ValidationObserver },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  data() {
    return {
      serviceType: '',
      url: '',
      serviceCode: '',
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    save(): void {
      this.$emit('save', this.serviceType, this.url, this.serviceCode);
      this.clear();
    },
    clear(): void {
      this.url = '';
      this.serviceCode = '';
      this.serviceType = '';
      requestAnimationFrame(() => {
        (this.$refs.form as InstanceType<typeof ValidationObserver>).reset();
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
