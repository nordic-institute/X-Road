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

            <ValidationProvider
              v-slot="{ errors }"
              ref="initializationParamsVP"
              name="init.identifier"
              rules="required"
              data-test="instance-identifier--validation"
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
              data-test="address--validation"
            >
              <v-text-field
                v-model="address"
                class="form-input"
                type="text"
                :label="$t('fields.init.address')"
                :error-messages="errors"
                :disabled="disabledFields.address"
                outlined
                data-test="address--input"
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
              data-test="pin--validation"
            >
              <v-text-field
                v-model="pin"
                class="form-input"
                type="password"
                autocomplete="pin-code"
                name="init.pin"
                :label="$t('fields.init.pin')"
                :error-messages="errors"
                :disabled="disabledFields.pin"
                outlined
                data-test="pin--input"
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
              data-test="confirm-pin--validation"
            >
              <v-text-field
                v-model="pinConfirm"
                class="form-input"
                type="password"
                autocomplete="pin-code"
                name="init.confirmPin"
                :label="$t('fields.init.confirmPin')"
                :error-messages="errors"
                :disabled="disabledFields.pin"
                outlined
                data-test="confirm-pin--input"
              >
                <xrd-icon-base
                  v-if="passed"
                  slot="append"
                  :color="colors.Success100"
                  data-test="confirm-pin-append-input-icon"
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
            :loading="submitting"
            data-test="submit-button"
            @click="submit"
            >{{ $t('action.submit') }}
          </xrd-button>
        </div>
      </v-form>
    </ValidationObserver>
  </main>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';
import { extend, ValidationObserver, ValidationProvider } from 'vee-validate';
import i18n from '@/i18n';
import { Colors, RouteName } from '@/global';
import {
  ErrorInfo,
  InitializationStatus,
  InitialServerConf,
  TokenInitStatus,
} from '@/openapi-types';
import { swallowRedirectedNavigationError } from '@/util/helpers';
import { AxiosError } from 'axios';
import { mapActions, mapState } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { systemStore } from '@/store/modules/system';

const PASSWORD_MATCH_ERROR: string = i18n.t('init.pin.pinMatchError') as string;

extend('password', {
  params: ['target'],
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  validate(value, { target }: Record<string, any>) {
    return value === target;
  },
  message: PASSWORD_MATCH_ERROR,
});

function getTranslatedFieldErrors(
  fieldName: string,
  fieldError: Record<string, string[]>,
): string[] {
  const errors: string[] = fieldError[fieldName];
  if (errors) {
    return errors.map((errorKey: string) => {
      return i18n.t(`validationError.${errorKey}Field`).toString();
    });
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
      submitting: false,
    };
  },
  computed: {
    ...mapState(systemStore, ['getSystemStatus']),
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
      this.pin = '****';
      this.pinConfirm = '****';
    }
    if (statusAtFirst?.central_server_address.length > 0) {
      this.disabledFields.address = true;
      this.address = statusAtFirst?.central_server_address;
    }
    if (statusAtFirst?.instance_identifier.length > 0) {
      this.disabledFields.instanceIdentifier = true;
      this.instanceIdentifier = statusAtFirst.instance_identifier;
    }
  },
  methods: {
    ...mapActions(notificationsStore, [
      'showError',
      'showSuccess',
      'resetNotifications',
    ]),
    ...mapActions(systemStore, ['fetchSystemStatus', 'initalizationRequest']),
    async submit() {
      // validate inputs
      const formData: InitialServerConf = {} as InitialServerConf;
      formData.instance_identifier = this.instanceIdentifier;
      formData.central_server_address = this.address;
      formData.software_token_pin = this.pin;
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
              this.$refs.initializationForm.setErrors({
                'init.identifier': identifierErrors,
                'init.address': addressErrors,
              });
              this.showError(error);
            }
            return;
          }

          this.showError(error);
        })
        .finally(() => {
          this.submitting = false;
          return this.fetchSystemStatus();
        });

      function isFieldError(error: ErrorInfo) {
        const errorStatus = error.status;
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
