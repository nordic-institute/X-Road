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
<!--
  Member details view
-->
<template>
  <validation-observer v-slot="{ invalid }">
    <xrd-simple-dialog
      title="securityServers.dialogs.editAddress.title"
      data-test="security-server-address-edit-dialog"
      save-button-text="action.save"
      :dialog="showDialog"
      :scrollable="false"
      :show-close="true"
      :loading="loading"
      :disable-save="invalid"
      @save="saveAddress"
      @cancel="close"
    >
      <div slot="content">
        <div class="pt-4 dlg-input-width">
          <validation-provider
            ref="serverAddressVP"
            v-slot="{ errors }"
            rules="required"
            name="securityServerAddress"
            class="validation-provider"
          >
            <v-text-field
              v-model="updatedAddress"
              data-test="security-server-address-edit-field"
              :label="$t('securityServers.dialogs.editAddress.addressField')"
              autofocus
              outlined
              class="dlg-row-input"
              name="securityServerAddress"
              :error-messages="errors"
            ></v-text-field>
          </validation-provider>
        </div>
      </div>
    </xrd-simple-dialog>
  </validation-observer>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { mapActions, mapStores } from 'pinia';
import { useSecurityServerStore } from '@/store/modules/security-servers';
import { notificationsStore } from '@/store/modules/notifications';
import { ErrorInfo } from '@/openapi-types';
import {
  getErrorInfo,
  getTranslatedFieldErrors,
  isFieldError,
} from '@/util/helpers';
import { AxiosError } from 'axios';

/**
 * Component for a Security server details view
 */
export default (
  Vue as VueConstructor<
    Vue & {
      $refs: {
        serverAddressVP: InstanceType<typeof ValidationProvider>;
      };
    }
  >
).extend({
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    securityServerId: {
      type: String,
      default: '',
    },
    address: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      showDialog: false,
      updatedAddress: this.address,
    };
  },
  computed: {
    ...mapStores(useSecurityServerStore),
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    open(): void {
      this.showDialog = true;
    },
    close(): void {
      this.updatedAddress = this.address;
      this.showDialog = false;
    },
    saveAddress: async function () {
      try {
        this.loading = true;
        await this.securityServerStore.updateAddress(
          this.securityServerId,
          this.updatedAddress,
        );
        this.showSuccess(
          this.$t('securityServers.dialogs.editAddress.success'),
        );
        this.close();
      } catch (updateError: unknown) {
        const errorInfo: ErrorInfo = getErrorInfo(updateError as AxiosError);
        if (isFieldError(errorInfo)) {
          // backend validation error
          let fieldErrors = errorInfo.error?.validation_errors;
          if (fieldErrors && this.$refs?.serverAddressVP) {
            this.$refs.serverAddressVP.setErrors(
              getTranslatedFieldErrors(
                'securityServerAddressDto.serverAddress',
                fieldErrors,
              ),
            );
          }
        } else {
          this.showError(updateError);
          this.close();
        }
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>

<style lang="scss" scoped></style>
