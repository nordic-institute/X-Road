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
  <xrd-simple-dialog
    title="systemSettings.editCentralServerAddressTitle"
    data-test="system-settings-central-server-address-edit-dialog"
    :scrollable="false"
    :show-close="true"
    :loading="saving"
    :disable-save="!meta.valid || !meta.dirty"
    save-button-text="action.save"
    @save="onServerAddressSave"
    @cancel="onCancelAddressEdit"
  >
    <template #content>
      <v-text-field
        v-bind="renewedServerAddress"
        data-test="system-settings-central-server-address-edit-field"
        :label="$t('systemSettings.centralServerAddress')"
        autofocus
        variant="outlined"
        class="dlg-row-input"
        name="serviceAddress"
        :error-messages="errors.serviceAddress"
      ></v-text-field>
    </template>
  </xrd-simple-dialog>
</template>
<script lang="ts">
import { defineComponent } from 'vue';
import { useForm } from 'vee-validate';
import { ErrorInfo } from '@/openapi-types';
import {
  getErrorInfo,
  getTranslatedFieldErrors,
  isFieldError,
} from '@/util/helpers';
import { AxiosError } from 'axios';
import { mapActions, mapState } from 'pinia';
import { useSystem } from '@/store/modules/system';
import { useNotifications } from '@/store/modules/notifications';
import { Event } from '@/ui-types';

export default defineComponent({
  props: {
    serviceAddress: {
      type: String,
      required: true,
    },
  },
  emits: [Event.Cancel, Event.Edit],
  setup(props) {
    const { meta, values, errors, setFieldError, defineComponentBinds } =
      useForm({
        validationSchema: { serviceAddress: 'required' },
        initialValues: { serviceAddress: props.serviceAddress },
      });
    const renewedServerAddress = defineComponentBinds('serviceAddress');
    return { meta, values, errors, setFieldError, renewedServerAddress };
  },
  data() {
    return {
      saving: false,
    };
  },
  computed: {
    ...mapState(useSystem, ['getSystemStatus']),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useSystem, [
      'fetchSystemStatus',
      'updateCentralServerAddress',
    ]),
    async onServerAddressSave(): Promise<void> {
      this.saving = true;
      try {
        await this.updateCentralServerAddress({
          central_server_address: this.values.serviceAddress,
        });

        this.showSuccess(
          this.$t('systemSettings.editCentralServerAddressSuccess'),
        );
        this.saving = false;
        this.$emit(Event.Edit);
      } catch (updateError: unknown) {
        const errorInfo: ErrorInfo = getErrorInfo(updateError as AxiosError);
        if (isFieldError(errorInfo)) {
          // backend validation error
          let fieldErrors = errorInfo.error?.validation_errors;
          if (fieldErrors && this.$refs?.serverAddressVP) {
            this.setFieldError(
              'serviceAddress',
              getTranslatedFieldErrors(
                'serverAddressUpdateBody.centralServerAddress',
                fieldErrors,
              ),
            );
          }
        } else {
          this.showError(updateError);
          this.$emit(Event.Cancel);
        }
        return;
      }
    },
    onCancelAddressEdit(): void {
      this.$emit(Event.Cancel);
    },
  },
});
</script>
