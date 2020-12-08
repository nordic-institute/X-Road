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
    <ValidationObserver ref="form2" v-slot="{ invalid }">
      <div class="wizard-step-form-content">
        <div v-for="item in csrForm" v-bind:key="item.id" class="row-wrap">
          <div class="label">
            {{ $t('certificateProfile.' + item.label_key) }}
          </div>

          <div>
            <ValidationProvider
              :name="item.id"
              :rules="item.required && 'required'"
              v-slot="{ errors }"
            >
              <v-text-field
                class="form-input"
                :name="item.id"
                type="text"
                outlined
                v-model="item.default_value"
                :disabled="item.read_only"
                :error-messages="errors"
                data-test="dynamic-csr-input"
                autofocus
              ></v-text-field>
            </ValidationProvider>
          </div>
        </div>
        <div class="generate-row">
          <div>{{ $t('csr.saveInfo') }}</div>
          <large-button
            @click="generateCsr"
            :disabled="invalid || !disableDone"
            data-test="generate-csr-button"
            >{{ $t('csr.generateCsr') }}</large-button
          >
        </div>
      </div>
      <div class="button-footer">
        <large-button
          outlined
          @click="cancel"
          :disabled="!disableDone"
          data-test="cancel-button"
          >{{ $t('action.cancel') }}</large-button
        >

        <large-button
          @click="previous"
          outlined
          class="previous-button"
          data-test="previous-button"
          :disabled="!disableDone"
          >{{ $t('action.previous') }}</large-button
        >
        <large-button
          @click="done"
          :disabled="disableDone"
          data-test="save-button"
          >{{ $t(saveButtonText) }}</large-button
        >
      </div>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { ValidationProvider, ValidationObserver } from 'vee-validate';

export default Vue.extend({
  components: {
    ValidationObserver,
    ValidationProvider,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.done',
    },
    // Creating Key + CSR or just CSR
    keyAndCsr: {
      type: Boolean,
      default: false,
    },
  },
  computed: {
    ...mapGetters(['csrForm']),
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
      const tokenId = this.$store.getters.csrTokenId;
      this.disableDone = false;
      if (this.keyAndCsr) {
        // Create key and CSR
        this.$store.dispatch('generateKeyAndCsr', tokenId).then(
          () => {
            // noop
          },
          (error) => {
            this.disableDone = true;
            this.$store.dispatch('showError', error);
          },
        );
      } else {
        // Create only CSR
        this.$store.dispatch('generateCsr').then(
          () => {
            // noop
          },
          (error) => {
            this.disableDone = true;
            this.$store.dispatch('showError', error);
          },
        );
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

.generate-row {
  margin-top: 40px;
  width: 840px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}
</style>
