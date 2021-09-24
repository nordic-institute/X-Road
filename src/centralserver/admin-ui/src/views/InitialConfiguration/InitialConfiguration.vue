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
    <ValidationObserver ref="initializationForm" v-slot="{ invalid }">
      <div class="form-content-wrap">
        <div class="form-main-title">{{ $t('init.initialConfiguration') }}</div>

        <div class="form-sub-title">{{ $t('init.csIdentification') }}</div>
        <div class="form-row-wrap">
          <xrd-form-label
            :label-text="$t('fields.init.identifier')"
            :help-text="$t('init.instanceIdentifier.info')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            ref="initializationParamsVP"
            name="init.identifier"
            rules="required"
          >
            <v-text-field
              v-model="instanceIdentifier"
              class="form-input"
              type="text"
              :label="$t('fields.init.identifier')"
              :error-messages="errors"
              :disabled="disabledFields.instanceIdentifier"
              outlined
              autofocus
              data-test="instance-identifier--input"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="form-row-wrap">
          <xrd-form-label
            :label-text="$t('fields.init.address')"
            :help-text="$t('init.address.info')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            ref="initializationParamsVP"
            name="init.address"
            rules="required"
          >
            <v-text-field
              v-model="address"
              class="form-input"
              type="text"
              :label="$t('fields.init.address')"
              :error-messages="errors"
              :disabled="disabledFields.address"
              outlined
              data-test="address-input"
            ></v-text-field>
          </ValidationProvider>
        </div>
        <div class="form-sub-title">{{ $t('init.softwareToken') }}</div>
        <div class="form-row-wrap">
          <xrd-form-label
            :label-text="$t('fields.init.pin')"
            :help-text="$t('init.pin.info')"
          />

          <ValidationProvider
            v-slot="{ errors }"
            name="init.pin"
            rules="required"
          >
            <v-text-field
              v-model="pin"
              class="form-input"
              type="password"
              name="init.pin"
              :label="$t('fields.init.pin')"
              :error-messages="errors"
              :disabled="disabledFields.pin"
              outlined
              data-test="pin-input"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="form-row-wrap">
          <xrd-form-label :label-text="$t('fields.init.confirmPin')" />

          <ValidationProvider
            v-slot="{ errors, passed }"
            ref="confirmPinFieldVP"
            name="init.confirmPin"
            rules="required|password:@init.pin"
          >
            <v-text-field
              v-model="pinConfirm"
              class="form-input"
              type="password"
              name="init.confirmPin"
              :label="$t('fields.init.confirmPin')"
              :error-messages="errors"
              :disabled="disabledFields.pin"
              outlined
              data-test="confirm-pin-input"
            >
              <xrd-icon-base
                v-if="passed"
                slot="append"
                :color="colors.Success100"
              >
                <XrdIconChecked />
              </xrd-icon-base>
            </v-text-field>
          </ValidationProvider>
        </div>
      </div>
      <div class="button-footer">
        <xrd-button
          :disabled="invalid"
          data-test="submit-button"
          @click="submit"
          >{{ $t('action.submit') }}
        </xrd-button>
      </div>
    </ValidationObserver>
  </main>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';
import { extend, ValidationObserver, ValidationProvider } from 'vee-validate';
import i18n from '@/i18n';
import { Colors, RouteName, StoreTypes } from '@/global';
import { ErrorInfo, InitialServerConf, TokenInitStatus } from '@/openapi-types';
import { swallowRedirectedNavigationError } from '@/util/helpers';
import { AxiosError } from 'axios';
import { State } from '@/store/modules/initialization';

const PASSWORD_MATCH_ERROR: string = i18n.t('init.pin.pinMatchError') as string;

extend('password', {
  params: ['target'],
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  validate(value, { target }: Record<string, any>) {
    return value === target;
  },
  message: PASSWORD_MATCH_ERROR,
});
const statusState = {
  instanceId: 'instanceIdentifier',
  serverAddress: 'address',
  tokenInit: 'pin',
} as const;
function getTranslatedFieldErrors(
  fieldName: string,
  fieldError: Record<string, string[]>,
): string[] {
  let errors: string[] = fieldError[fieldName];
  if (errors) {
    return errors.map((errorKey: string) => {
      return i18n.t(`validationError.${errorKey}Field`).toString();
    });
  } else {
    return [];
  }
}

function invalidParamsErrors(errorInfo: ErrorInfo): Array<string> {
  if (
    400 === errorInfo.status &&
    'invalid_init_params' === errorInfo.error?.code
  ) {
    return errorInfo.error.metadata || [];
  } else {
    return [];
  }
}

export default (
  Vue as VueConstructor<
    Vue & {
      $refs: {
        initializationForm: InstanceType<typeof ValidationObserver>;
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
      colors: Colors,
      disabledFields: {
        address: false,
        instanceIdentifier: false,
        pin: false,
      },
    };
  },
  computed: {},
  created: function () {
    let statusAtFirst: State =
      this.$store.getters[StoreTypes.getters.INITIALIZATION_STATUS];

    if (TokenInitStatus.INITIALIZED == statusAtFirst?.tokenInit) {
      this.disabledFields.pin = true;
      this.pin = '****';
    }
    if (statusAtFirst?.serverAddress.length > 0) {
      this.disabledFields.address = true;
      this.address = statusAtFirst?.serverAddress;
    }
    if (statusAtFirst?.instanceId.length > 0) {
      this.disabledFields.instanceIdentifier = true;
      this.instanceIdentifier = statusAtFirst.instanceId;
    }
  },
  methods: {
    async submit() {
      // validate inputs

      const formData: InitialServerConf = {};
      if (!this.disabledFields.instanceIdentifier) {
        formData.instance_identifier = this.instanceIdentifier;
      }
      if (!this.disabledFields.address) {
        formData.central_server_address = this.address;
      }
      if (!this.disabledFields.pin) {
        formData.software_token_pin = this.pin;
      }
      await this.$store.dispatch(StoreTypes.actions.RESET_NOTIFICATIONS_STATE);
      await this.$store
        .dispatch(StoreTypes.actions.INITIALIZATION_REQUEST, formData)
        .then(
          () => {
            this.$router
              .push({
                name: RouteName.Members,
              })
              .catch(swallowRedirectedNavigationError);
          },
          (error: AxiosError) => {
            let errorInfo: ErrorInfo = error.response?.data || { status: 0 };

            if (isFieldError(errorInfo)) {
              let fieldErrors = errorInfo.error?.validation_errors;
              if (fieldErrors) {
                let identifierErrors: string[] = getTranslatedFieldErrors(
                  'initialServerConf.instanceIdentifier',
                  fieldErrors,
                );
                let addressErrors: string[] = getTranslatedFieldErrors(
                  'initialServerConf.centralServerAddress',
                  fieldErrors,
                );
                this.$refs.initializationForm.setErrors({
                  'init.identifier': identifierErrors,
                  'init.address': addressErrors,
                });
                this.$store.dispatch(StoreTypes.actions.SHOW_ERROR, error);
              }
              return;
            }
            if (invalidParamsErrors(errorInfo).length > 0) {
              this.$store.dispatch(StoreTypes.actions.SHOW_ERROR, error);
              return;
            }
            throw error;
          },
        )
        .catch((error) => {
          return this.$store.dispatch(
            StoreTypes.actions.SHOW_ERROR_MESSAGE_RAW,
            error,
          );
        })
        .finally(() => {
          return this.$store.dispatch(
            StoreTypes.actions.INITIALIZATION_STATUS_REQUEST,
          );
        });

      function isFieldError(error: ErrorInfo) {
        let errorStatus = error.status;
        return (
          400 === errorStatus && 'validation_failure' === error?.error?.code
        );
      }
    },
  },
});
</script>

<style lang="scss" scoped>
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
