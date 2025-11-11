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
  <XrdElevatedViewSimple
    id="initial-configuration"
    data-test="central-server-initialization-page"
    title="init.initialConfiguration"
  >
    <XrdWizardStep>
      <XrdFormBlock class="mb-4" title="init.csIdentification">
        <XrdFormBlockRow
          description="init.instanceIdentifier.info"
          adjust-against-content
        >
          <v-text-field
            v-bind="instanceIdentifier"
            data-test="instance-identifier--input"
            class="xrd"
            autofocus
            :label="$t('fields.init.identifier')"
            :error-messages="errors['init.identifier']"
            :disabled="disabledFields.instanceIdentifier"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow description="init.address.info" adjust-against-content>
          <v-text-field
            v-bind="address"
            data-test="address--input"
            class="xrd"
            :label="$t('fields.init.address')"
            :error-messages="errors['init.address']"
            :disabled="disabledFields.address"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
      <XrdFormBlock title="init.softwareToken">
        <XrdFormBlockRow description="init.pin.info" adjust-against-content>
          <v-text-field
            v-bind="pin"
            data-test="pin--input"
            class="xrd"
            autocomplete="pin-code"
            name="init.pin"
            :type="passwordType"
            :label="$t('fields.init.pin')"
            :error-messages="errors['init.pin']"
            :disabled="disabledFields.pin"
          >
            <template #append-inner>
              <v-icon
                color="primary"
                :icon="passwordIcon"
                @click.stop="togglePasswordType"
              />
            </template>
          </v-text-field>
        </XrdFormBlockRow>
        <XrdFormBlockRow>
          <v-text-field
            v-bind="pinConfirm"
            data-test="confirm-pin--input"
            class="xrd"
            :type="passwordType"
            autocomplete="pin-code"
            name="init.confirmPin"
            :label="$t('fields.init.confirmPin')"
            :error-messages="errors['init.confirmPin']"
            :disabled="disabledFields.pin"
          >
            <template #append-inner>
              <v-icon
                v-if="pinConfirmValid"
                class="mr-1"
                color="success"
                icon="check_circle filled"
                data-test="confirm-pin-append-input-icon"
              />
              <v-icon
                color="primary"
                :icon="passwordIcon"
                @click.stop="togglePasswordType"
              />
            </template>
          </v-text-field>
        </XrdFormBlockRow>
      </XrdFormBlock>
      <template #footer>
        <v-spacer />
        <XrdBtn
          data-test="submit-button"
          class="font-weight-medium"
          text="action.submit"
          color="secondary"
          :disabled="!meta.valid"
          :loading="submitting"
          @click="submit"
        />
      </template>
    </XrdWizardStep>
  </XrdElevatedViewSimple>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState } from 'pinia';
import { defineRule, useForm, useIsFieldValid } from 'vee-validate';
import { confirmed } from '@vee-validate/rules';

import {
  axiosHelpers,
  useNotifications,
  XrdBtn,
  XrdElevatedViewSimple,
  XrdWizardStep,
  XrdFormBlock,
  XrdFormBlockRow,
} from '@niis/shared-ui';

import { RouteName } from '@/global';
import {
  ErrorInfo,
  InitializationStatus,
  InitialServerConf,
  TokenInitStatus,
} from '@/openapi-types';
import { useSystem } from '@/store/modules/system';

defineRule('confirmed', confirmed);

export default defineComponent({
  components: {
    XrdBtn,
    XrdElevatedViewSimple,
    XrdWizardStep,
    XrdFormBlock,
    XrdFormBlockRow,
  },
  props: {},
  setup() {
    const {
      addError,
      addSuccessMessage,
      clear: clearNotifications,
    } = useNotifications();

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
        'init.address': 'required|address',
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
      addError,
      addSuccessMessage,
      clearNotifications,
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
      passwordType: 'password',
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
    passwordIcon() {
      return this.passwordType === 'password' ? 'visibility_off' : 'visibility';
    },
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
    ...mapActions(useSystem, ['fetchSystemStatus', 'initializationRequest']),
    togglePasswordType() {
      this.passwordType =
        this.passwordType === 'password' ? 'text' : 'password';
    },
    async submit() {
      // validate inputs
      const formData: InitialServerConf = {} as InitialServerConf;
      formData.instance_identifier = this.values.init.identifier;
      formData.central_server_address = this.values.init.address;
      formData.software_token_pin = this.values.init.pin;
      this.clearNotifications();
      this.submitting = true;
      await this.initializationRequest(formData)
        .then(() => {
          this.$router
            .push({
              name: RouteName.Members,
            });
        })
        .catch((error) => {
          const errorInfo: ErrorInfo = error.response?.data || { status: 0 };
          if (axiosHelpers.isFieldValidationError(errorInfo)) {
            const fieldErrors = errorInfo.error?.validation_errors;
            if (fieldErrors) {
              const identifierErrors: string[] =
                axiosHelpers.getTranslatedFieldErrors(
                  'initialServerConfDto.instanceIdentifier',
                  fieldErrors,
                );
              const addressErrors: string[] =
                axiosHelpers.getTranslatedFieldErrors(
                  'initialServerConfDto.centralServerAddress',
                  fieldErrors,
                );
              this.setFieldError('init.identifier', identifierErrors);
              this.setFieldError('init.address', addressErrors);
              this.addError(error);
            }
            return;
          } else if (isPinFieldError(errorInfo)) {
            this.setFieldError(
              'init.pin',
              this.$t('error_code.token_weak_pin'),
            );
          }

          this.addError(error);
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

<style lang="scss" scoped></style>
