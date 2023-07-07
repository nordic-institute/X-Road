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
  <div data-test="system-settings-view">
    <!-- Title  -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('tab.settings.systemSettings') }}
      </div>
    </div>

    <div>
      <!-- System Parameters -->
      <div
        id="system-parameters-anchor"
        class="mb-6"
        data-test="system-settings-system-parameters-card"
      >
        <v-card class="pb-4" flat>
          <div class="card-top">
            <div class="card-main-title">
              {{ $t('systemSettings.systemParameters') }}
            </div>
          </div>

          <table class="xrd-table mt-0 pb-3">
            <tbody>
              <tr>
                <td class="title-cell">
                  <div>
                    <div>
                      {{ $t('systemSettings.instanceIdentifier') }}
                    </div>
                  </div>
                </td>
                <td data-test="system-settings-instance-identifier-field">
                  {{ instanceIdentifier }}
                </td>
                <td></td>
              </tr>
              <tr>
                <td>
                  <div>
                    <div>
                      {{ $t('systemSettings.centralServerAddress') }}
                    </div>
                  </div>
                </td>
                <td data-test="system-settings-central-server-address-field">
                  {{ serverAddress }}
                </td>
                <td class="action-cell">
                  <xrd-button
                    text
                    data-test="system-settings-central-server-address-edit-button"
                    :outlined="false"
                    @click="onServerAddressEdit"
                    >{{ $t('action.edit') }}
                  </xrd-button>
                </td>
              </tr>
            </tbody>
          </table>
        </v-card>
      </div>

      <xrd-simple-dialog
        v-if="isEditingServerAddress"
        title="systemSettings.editCentralServerAddressTitle"
        data-test="system-settings-central-server-address-edit-dialog"
        :dialog="isEditingServerAddress"
        :scrollable="false"
        :show-close="true"
        :loading="saveInProgress"
        save-button-text="action.save"
        @save="onServerAddressSave(renewedServerAddress)"
        @cancel="onCancelAddressEdit"
      >
        <div slot="content">
          <div class="pt-4 dlg-input-width">
            <ValidationProvider
              ref="serverAddressVP"
              v-slot="{ errors }"
              rules="required"
              name="serviceAddress"
              class="validation-provider"
            >
              <v-text-field
                v-model="renewedServerAddress"
                data-test="system-settings-central-server-address-edit-field"
                :label="$t('systemSettings.centralServerAddress')"
                autofocus
                outlined
                class="dlg-row-input"
                name="serviceAddress"
                :error-messages="errors"
              ></v-text-field>
            </ValidationProvider>
          </div>
        </div>
      </xrd-simple-dialog>
    </div>

    <!-- Management Services -->
    <ManagementServices ref="managementServices" />
    <MemberClasses />
  </div>
</template>

<script lang="ts">
import { ErrorInfo } from '@/openapi-types';
import { managementServicesStore } from '@/store/modules/management-services';
import { notificationsStore } from '@/store/modules/notifications';
import { systemStore } from '@/store/modules/system';

import {
  getErrorInfo,
  getTranslatedFieldErrors,
  isFieldError,
} from '@/util/helpers';
import ManagementServices from '@/components/systemSettings/ManagementServices.vue';
import MemberClasses from '@/components/systemSettings/MemberClasses.vue';
import { AxiosError } from 'axios';
import { mapActions, mapState } from 'pinia';
import { ValidationProvider } from 'vee-validate';

/**
 * View for 'system settings' tab
 */
import Vue, { VueConstructor } from 'vue';

export default (
  Vue as VueConstructor<
    Vue & {
      $refs: {
        serverAddressVP: InstanceType<typeof ValidationProvider>;
        managementServices: InstanceType<typeof ManagementServices>;
      };
    }
  >
).extend({
  components: {
    MemberClasses,
    ValidationProvider,
    ManagementServices,
  },
  data() {
    return {
      search: '' as string,
      showOnlyPending: false,
      saveInProgress: false,
      isEditingServerAddress: false,
      renewedServerAddress: '',
    };
  },
  computed: {
    ...mapState(systemStore, ['getSystemStatus']),
    serverAddress(): string | undefined {
      return this.getSystemStatus?.initialization_status
        ?.central_server_address;
    },
    instanceIdentifier(): string | undefined {
      return this.getSystemStatus?.initialization_status?.instance_identifier;
    },
  },
  created() {
    this.fetchSystemStatus();
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    ...mapActions(systemStore, [
      'fetchSystemStatus',
      'updateCentralServerAddress',
    ]),
    ...mapActions(managementServicesStore, [
      'fetchManagementServicesConfiguration',
    ]),
    async onServerAddressSave(serverAddress: string): Promise<void> {
      this.saveInProgress = true;
      try {
        await this.updateCentralServerAddress({
          central_server_address: serverAddress,
        });

        await this.fetchSystemStatus();
        this.$refs.managementServices.fetchManagementServicesConfiguration();

        this.showSuccess(
          this.$t('systemSettings.editCentralServerAddressSuccess'),
        );
        this.saveInProgress = false;
        this.isEditingServerAddress = false;
      } catch (updateError: unknown) {
        const errorInfo: ErrorInfo = getErrorInfo(updateError as AxiosError);
        if (isFieldError(errorInfo)) {
          // backend validation error
          let fieldErrors = errorInfo.error?.validation_errors;
          if (fieldErrors && this.$refs?.serverAddressVP) {
            this.$refs.serverAddressVP.setErrors(
              getTranslatedFieldErrors(
                'serverAddressUpdateBody.centralServerAddress',
                fieldErrors,
              ),
            );
          }
          this.isEditingServerAddress = true;
        } else {
          this.showError(updateError);
          this.isEditingServerAddress = false;
        }
        return;
      }
    },
    onServerAddressEdit(): void {
      this.renewedServerAddress = this.serverAddress ? this.serverAddress : '';
      this.isEditingServerAddress = true;
    },
    onCancelAddressEdit(): void {
      this.isEditingServerAddress = false;
    },
  },
});
</script>
<style lang="scss" scoped>
@import '~styles/tables';

.align-fix {
  align-items: center;
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.title-cell {
  max-width: 40%;
  width: 40%;
}

.action-cell {
  text-align: right;
  width: 100px;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}
</style>
