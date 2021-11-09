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
  <ValidationObserver v-if="dialog" ref="form" v-slot="{ invalid }">
    <xrd-simple-dialog
      :dialog="dialog"
      :width="750"
      title="endpoints.addEndpoint"
      :disable-save="invalid"
      @save="save"
      @cancel="cancel"
    >
      <div slot="content">
        <div class="dlg-edit-row">
          <v-select
            v-model="method"
            class="ml-2"
            data-test="endpoint-method"
            :label="$t('endpoints.httpRequestMethod')"
            autofocus
            outlined
            :items="methods"
          />
        </div>

        <div class="dlg-edit-row">
          <ValidationProvider
            ref="path"
            v-slot="{ errors }"
            rules="required|xrdEndpoint"
            name="path"
            class="validation-provider"
          >
            <v-text-field
              v-model="path"
              class="ml-2"
              outlined
              :label="$t('endpoints.path')"
              :error-messages="errors"
              name="path"
              data-test="endpoint-path"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="pl-2">
          <div>
            <div>{{ $t('endpoints.endpointHelp1') }}</div>
            <div>{{ $t('endpoints.endpointHelp2') }}</div>
            <div>{{ $t('endpoints.endpointHelp3') }}</div>
            <div>{{ $t('endpoints.endpointHelp4') }}</div>
          </div>
        </div>
      </div>
    </xrd-simple-dialog>
  </ValidationObserver>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationObserver, ValidationProvider } from 'vee-validate';

export default Vue.extend({
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  data() {
    return {
      methods: [
        { text: this.$t('endpoints.all'), value: '*' },
        { text: 'GET', value: 'GET' },
        { text: 'POST', value: 'POST' },
        { text: 'PUT', value: 'PUT' },
        { text: 'PATCH', value: 'PATCH' },
        { text: 'DELETE', value: 'DELETE' },
        { text: 'HEAD', value: 'HEAD' },
        { text: 'OPTIONS', value: 'OPTIONS' },
        { text: 'TRACE', value: 'TRACE' },
      ],
      method: '*',
      path: '/',
    };
  },
  methods: {
    save(): void {
      this.$emit('save', this.method, this.path);
      this.clear();
    },
    cancel(): void {
      this.$emit('cancel');
      this.clear();
    },
    clear(): void {
      this.path = '/';
      this.method = '*';
      (this.$refs.form as InstanceType<typeof ValidationObserver>).reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';

.dlg-edit-row {
  width: 400px;
}
</style>
