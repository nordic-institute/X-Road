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
  <main id="initial-configuration" class="form-view-wrap">
    <ValidationObserver ref="form1" v-slot="{ invalid }">
      <div class="form-content-wrap">
        <div class="form-main-title">{{ $t('init.initialConfiguration') }}</div>

        <div class="form-sub-title">{{ $t('init.csIdentification') }}</div>
        <div class="form-row-wrap">
          <xrd-form-label
            :labelText="$t('fields.init.identifier')"
            :helpText="$t('init.instanceIdentifier.info')"
          />

          <ValidationProvider
            name="init.identifier"
            rules="required"
            v-slot="{ errors }"
            ref="memberCodeVP"
          >
            <v-text-field
              class="form-input"
              type="text"
              :error-messages="errors"
              v-model="instanceIdentifier"
              outlined
              autofocus
              data-test="instance-identifier--input"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="form-row-wrap">
          <xrd-form-label
            :labelText="$t('fields.init.address')"
            :helpText="$t('init.address.info')"
          />

          <ValidationProvider
            name="init.address"
            rules="required"
            v-slot="{ errors }"
            ref="memberCodeVP"
          >
            <v-text-field
              class="form-input"
              type="text"
              :error-messages="errors"
              v-model="address"
              outlined
              data-test="address-input"
            ></v-text-field>
          </ValidationProvider>
        </div>
        <div class="form-sub-title">{{ $t('init.softwareToken') }}</div>
        <div class="form-row-wrap">
          <xrd-form-label
            :labelText="$t('fields.init.pin')"
            :helpText="$t('init.pin.info')"
          />

          <ValidationProvider
            name="init.pin"
            rules="required|password:@init.confirmPin"
            v-slot="{ errors }"
          >
            <v-text-field
              class="form-input"
              type="text"
              name="init.pin"
              :error-messages="errors"
              v-model="pin"
              outlined
              data-test="pin-input"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="form-row-wrap">
          <xrd-form-label :labelText="$t('fields.init.repeatPin')" />

          <ValidationProvider
            name="init.confirmPin"
            rules="required"
            v-slot="{ errors }"
          >
            <v-text-field
              class="form-input"
              type="text"
              name="init.confirmPin"
              :error-messages="errors"
              v-model="pinConfirm"
              outlined
              data-test="confirm-pin-input"
            ></v-text-field>
          </ValidationProvider>
        </div>
      </div>
      <div class="button-footer">
        <xrd-button
          :disabled="invalid"
          @click="submit"
          data-test="submit-button"
          >{{ $t('action.submit') }}</xrd-button
        >
      </div>
    </ValidationObserver>
  </main>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';
import { ValidationProvider, ValidationObserver, extend } from 'vee-validate';
import i18n from '@/i18n';
import { StoreTypes } from '@/global';

const PASSWORD_MATCH_ERROR: string = i18n.t('init.pin.pinMatchError') as string;

extend('password', {
  params: ['target'],
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  validate(value, { target }: Record<string, any>) {
    return value === target;
  },
  message: PASSWORD_MATCH_ERROR,
});

export default (
  Vue as VueConstructor<
    Vue & {
      $refs: {
        memberCodeVP: InstanceType<typeof ValidationProvider>;
      };
    }
  >
).extend({
  components: {
    ValidationObserver,
    ValidationProvider,
  },
  props: {},
  data() {
    return {
      address: '',
      instanceIdentifier: '',
      pin: '',
      pinConfirm: '',
    };
  },
  computed: {},
  methods: {
    submit(): void {
      // Add submit actions
      this.$store.commit(StoreTypes.mutations.SET_CONTINUE_INIT, true);
    },
  },
});
</script>

<style lang="scss">
@import '~styles/colors';
@import '~styles/forms';

.form-main-title {
  color: $XRoad-WarmGrey100;
  width: 100%;
  padding: 24px;
  font-style: normal;
  font-weight: bold;
  font-size: 24px;
  line-height: 32px;
}

.form-sub-title {
  color: $XRoad-WarmGrey100;
  width: 100%;
  padding: 24px;
  margin-left: 12%;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;

  @media only screen and (max-width: 1200px) {
    // Keeps the titles somewhat in line with the descriptions
    margin-left: 0;
  }
}
</style>
