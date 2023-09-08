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
    <v-form>
      <div class="form-content-wrap">
        <div
          class="form-main-title"
          data-test="central-server-initialization-page-title"
        >
          {{ $t('init.initialConfiguration') }}
        </div>

        <div class="form-sub-title">{{ $t('init.csIdentification') }}</div>

        <div class="form-row-wrap">
          <xrd-form-label
            :label-text="$t('fields.init.identifier')"
            :help-text="$t('init.instanceIdentifier.info')"
          />

          <v-text-field
            v-bind="instanceIdentifier"
            class="form-input"
            type="text"
            :label="$t('fields.init.identifier')"
            :error-messages="errors['init.identifier']"
            :disabled="disabledFields.instanceIdentifier"
            variant="outlined"
            autofocus
            data-test="instance-identifier--input"
          />
        </div>

        <div class="form-row-wrap">
          <xrd-form-label
            :label-text="$t('fields.init.address')"
            :help-text="$t('init.address.info')"
          />

          <v-text-field
            v-bind="address"
            class="form-input"
            type="text"
            :label="$t('fields.init.address')"
            :error-messages="errors['init.address']"
            :disabled="disabledFields.address"
            variant="outlined"
            data-test="address--input"
          />
        </div>
        <div class="form-sub-title">{{ $t('init.softwareToken') }}</div>
        <div class="form-row-wrap">
          <xrd-form-label
            :label-text="$t('fields.init.pin')"
            :help-text="$t('init.pin.info')"
          />

          <v-text-field
            v-bind="pin"
            class="form-input"
            type="password"
            autocomplete="pin-code"
            name="init.pin"
            :label="$t('fields.init.pin')"
            :error-messages="errors['init.pin']"
            :disabled="disabledFields.pin"
            variant="outlined"
            data-test="pin--input"
          />
        </div>

        <div class="form-row-wrap">
          <xrd-form-label :label-text="$t('fields.init.confirmPin')" />
          <v-text-field
            v-bind="pinConfirm"
            class="form-input"
            type="password"
            autocomplete="pin-code"
            name="init.confirmPin"
            :label="$t('fields.init.confirmPin')"
            :error-messages="errors['init.confirmPin']"
            :disabled="disabledFields.pin"
            variant="outlined"
            data-test="confirm-pin--input"
          >
            <template v-if="pinConfirmValid" #append-inner>
              <xrd-icon-base
                :color="colors.Success100"
                data-test="confirm-pin-append-input-icon"
              >
                <xrd-icon-checked />
              </xrd-icon-base>
            </template>
          </v-text-field>
        </div>
      </div>
      <div class="button-footer">
        <xrd-button
          :disabled="!meta.valid"
          :loading="submitting"
          data-test="submit-button"
          @click="submit"
          >{{ $t('action.submit') }}
        </xrd-button>
      </div>
    </v-form>
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Colors, RouteName } from '@/global';
import { ErrorInfo, InitializationStatus, InitialServerConf, TokenInitStatus, } from '@/openapi-types';
import { getTranslatedFieldErrors, isFieldError, swallowRedirectedNavigationError, } from '@/util/helpers';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useSystem } from '@/store/modules/system';
import { defineRule, useForm, useIsFieldValid } from 'vee-validate';
import { XrdFormLabel } from '@niis/shared-ui';
import { confirmed } from '@vee-validate/rules';

defineRule('confirmed', confirmed);

export default defineComponent({
  components: { XrdFormLabel },
  props: {},
  setup() {
    const {
      defineComponentBinds,
      errors,
      meta,
      values,
      setFieldValue,
      setFieldError,
    } = useForm({
      validationSchema: {
        'init.identifier': 'required',
        'init.address': 'required',
        'init.pin': 'required',
        'init.confirmPin': 'required|confirmed:@init.pin',
      },
    });
    const instanceIdentifier = defineComponentBinds('init.identifier');
    const address = defineComponentBinds('init.address');
    const pin = defineComponentBinds('init.pin');
    const pinConfirm = defineComponentBinds('init.confirmPin');
    const pinConfirmValid = useIsFieldValid('init.confirmPin');
    return {
      instanceIdentifier,
      address,
      pin,
      pinConfirm,
      errors,
      meta,
      values,
      setFieldValue,
      setFieldError,
      pinConfirmValid,
    };
  },
  data() {
    return {
      colors: Colors,
      disabledFields: {
        address: false,
        instanceIdentifier: false,
        pin: false,
      },
      submitting: false,
    };
  },
  computed: {
    ...mapState(useSystem, ['getSystemStatus']),
  },
  created() {
    if (!this.getSystemStatus?.initialization_status) {
      // should not happen
      return;
    }

    const statusAtFirst: InitializationStatus = this.getSystemStatus
      ?.initialization_status as InitializationStatus;

    if (
      TokenInitStatus.INITIALIZED == statusAtFirst?.software_token_init_status
    ) {
      this.disabledFields.pin = true;
      this.setFieldValue('init.pin', '****');
      this.setFieldValue('init.confirmPin', '****');
    }
    if (statusAtFirst?.central_server_address.length > 0) {
      this.disabledFields.address = true;
      this.setFieldValue('init.address', statusAtFirst?.central_server_address);
    }
    if (statusAtFirst?.instance_identifier.length > 0) {
      this.disabledFields.instanceIdentifier = true;
      this.setFieldValue('init.identifier', statusAtFirst.instance_identifier);
    }
  },
  methods: {
    ...mapActions(useNotifications, [
      'showError',
      'showSuccess',
      'resetNotifications',
    ]),
    ...mapActions(useSystem, ['fetchSystemStatus', 'initalizationRequest']),
    async submit() {
      // validate inputs
      const formData: InitialServerConf = {} as InitialServerConf;
      formData.instance_identifier = this.values.init.identifier;
      formData.central_server_address = this.values.init.address;
      formData.software_token_pin = this.values.init.pin;
      this.resetNotifications();
      this.submitting = true;
      await this.initalizationRequest(formData)
        .then(() => {
          this.$router
            .push({
              name: RouteName.Members,
            })
            .catch(swallowRedirectedNavigationError);
        })
        .catch((error) => {
          const errorInfo: ErrorInfo = error.response?.data || { status: 0 };
          if (isFieldError(errorInfo)) {
            const fieldErrors = errorInfo.error?.validation_errors;
            if (fieldErrors) {
              const identifierErrors: string[] = getTranslatedFieldErrors(
                'initialServerConfDto.instanceIdentifier',
                fieldErrors,
              );
              const addressErrors: string[] = getTranslatedFieldErrors(
                'initialServerConfDto.centralServerAddress',
                fieldErrors,
              );
              this.setFieldError('init.identifier', identifierErrors);
              this.setFieldError('init.address', addressErrors);
              this.showError(error);
            }
            return;
          } else if (isPinFieldError(errorInfo)) {
            this.setFieldError(
              'init.pin',
              this.$t('error_code.token_weak_pin'),
            );
          }

          this.showError(error);
        })
        .finally(() => {
          this.submitting = false;
          return this.fetchSystemStatus();
        });

      function isPinFieldError(error: ErrorInfo) {
        const errorStatus = error.status;
        return 400 === errorStatus && 'token_weak_pin' === error?.error?.code;
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/forms';

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
