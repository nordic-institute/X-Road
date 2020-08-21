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
  <simpleDialog
    :dialog="dialog"
    title="keys.registrationRequest"
    @save="save"
    @cancel="cancel"
    :disableSave="!isValid"
  >
    <div slot="content">
      <ValidationObserver ref="form" v-slot="{}">
        <div class="dlg-edit-row">
          <div class="dlg-row-title">{{ $t('keys.certRegistrationInfo') }}</div>

          <ValidationProvider
            rules="required"
            name="dns"
            v-slot="{ errors }"
            class="validation-provider dlg-row-input"
          >
            <v-text-field
              v-model="url"
              single-line
              name="dns"
              :error-messages="errors"
            ></v-text-field>
          </ValidationProvider>
        </div>
      </ValidationObserver>
    </div>
  </simpleDialog>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import SimpleDialog from '@/components/ui/SimpleDialog.vue';

export default Vue.extend({
  components: { SimpleDialog, ValidationProvider, ValidationObserver },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
  },
  data() {
    return {
      url: '',
    };
  },
  computed: {
    isValid(): boolean {
      if (this.url && this.url.length > 0) {
        return true;
      }

      return false;
    },
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');

      this.clear();
    },
    save(): void {
      this.$emit('save', this.url);
      this.clear();
    },
    clear(): void {
      this.url = '';
      (this.$refs.form as InstanceType<typeof ValidationObserver>).reset();
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';
</style>
