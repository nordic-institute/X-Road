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
  <div class="step-content-wrapper">
    <div class="wizard-step-form-content">
      <div>{{ $t('initialConfiguration.pin.info1') }}</div>

      <v-alert
        v-if="isEnforceTokenPolicyEnabled"
        data-test="alert-token-policy-enabled"
        class="mt-6"
        variant="outlined"
        border="start"
        density="compact"
        type="info"
      >
        <h4>{{ $t('token.tokenPinPolicyHeader') }}</h4>
        <div>{{ $t('token.tokenPinPolicy') }}</div>
      </v-alert>

      <div class="mt-6 mb-4">
        <v-text-field
          v-bind="pinRef"
          class="wizard-form-input"
          autofocus="true"
          :label="$t('initialConfiguration.pin.pin')"
          type="password"
          data-test="pin-input"
        />
      </div>
      <div class="mb-6">
        <v-text-field
          v-bind="confirmPinRef"
          class="wizard-form-input"
          :label="$t('initialConfiguration.pin.confirmPin')"
          type="password"
          data-test="confirm-pin-input"
        />
      </div>

      {{ $t('initialConfiguration.pin.info2') }}
      <br/>
      <br/>
      {{ $t('initialConfiguration.pin.info3') }}
    </div>
    <div class="button-footer">
      <v-spacer></v-spacer>
      <div>
        <xrd-button
          outlined
          class="previous-button"
          data-test="previous-button"
          @click="previous"
        >{{ $t('action.previous') }}
        </xrd-button
        >
        <xrd-button
          :disabled="!meta.valid"
          :loading="saveBusy"
          data-test="token-pin-save-button"
          @click="done"
        >{{ $t('action.submit') }}
        </xrd-button
        >
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import {defineComponent} from 'vue';
import {PublicPathState, useForm} from 'vee-validate';
import {mapState} from "pinia";
import {useUser} from "@/store/modules/user";

export default defineComponent({
  props: {
    saveBusy: {
      type: Boolean,
    },
  },
  emits: ['done', 'previous'],
  setup() {
    const {meta, values, defineComponentBinds} = useForm({
      validationSchema: {
        pin: 'required',
        confirmPin: 'required|confirmed:@pin',
      },
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const pinRef = defineComponentBinds('pin', componentConfig);
    const confirmPinRef = defineComponentBinds('confirmPin', componentConfig);
    return {meta, values, pinRef, confirmPinRef};
  },
  computed: {
    ...mapState(useUser, ['isEnforceTokenPolicyEnabled',]),
  },
  methods: {
    done(): void {
      this.$emit('done', this.values.pin);
    },
    previous(): void {
      this.$emit('previous');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>
