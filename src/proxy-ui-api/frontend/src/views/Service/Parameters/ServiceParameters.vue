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
  <div class="xrd-tab-max-width xrd-view-common">
    <div class="apply-to-all" v-if="showApplyToAll">
      <div class="apply-to-all-text">{{ $t('services.applyToAll') }}</div>
    </div>

    <ValidationObserver ref="form" v-slot="{ validate, invalid }">
      <div class="edit-row">
        <div class="edit-title">
          {{ $t('services.serviceUrl') }}
          <helpIcon :text="$t('services.urlTooltip')" />
        </div>

        <div class="edit-input">
          <ValidationProvider
            rules="required|wsdlUrl"
            name="serviceUrl"
            class="validation-provider"
            v-slot="{ errors }"
          >
            <v-text-field
              v-model="service.url"
              @input="changeUrl()"
              single-line
              class="description-input"
              name="serviceUrl"
              :error-messages="errors"
              data-test="service-url"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <v-checkbox
          v-if="showApplyToAll"
          @change="setTouched()"
          v-model="url_all"
          color="primary"
          class="table-checkbox"
          data-test="url-all"
        ></v-checkbox>
      </div>

      <div class="edit-row">
        <div class="edit-title">
          {{ $t('services.timeoutSec') }}
          <helpIcon :text="$t('services.timeoutTooltip')" />
        </div>
        <div class="edit-input">
          <ValidationProvider
            :rules="{ required: true, between: { min: 0, max: 1000 } }"
            name="serviceTimeout"
            class="validation-provider"
            v-slot="{ errors }"
          >
            <v-text-field
              v-model="service.timeout"
              single-line
              @input="setTouched()"
              type="number"
              style="max-width: 200px"
              name="serviceTimeout"
              :error-messages="errors"
              data-test="service-timeout"
            ></v-text-field>
          </ValidationProvider>
          <!-- 0 - 1000 -->
        </div>

        <v-checkbox
          v-if="showApplyToAll"
          @change="setTouched()"
          v-model="timeout_all"
          color="primary"
          class="table-checkbox"
          data-test="timeout-all"
        ></v-checkbox>
      </div>

      <div class="edit-row">
        <div class="edit-title">
          {{ $t('services.verifyTls') }}
          <helpIcon :text="$t('services.tlsTooltip')" />
        </div>
        <div class="edit-input">
          <v-checkbox
            :disabled="!isHttpsMethod()"
            @change="setTouched()"
            v-model="service.ssl_auth"
            color="primary"
            class="table-checkbox"
            data-test="ssl-auth"
          ></v-checkbox>
        </div>

        <v-checkbox
          v-if="showApplyToAll"
          @change="setTouched()"
          v-model="ssl_auth_all"
          color="primary"
          class="table-checkbox"
          data-test="ssl-auth-all"
        ></v-checkbox>
      </div>

      <div class="button-wrap">
        <large-button
          :disabled="invalid || disableSave"
          :loading="saving"
          @click="save(false)"
          data-test="save-service-parameters"
          >{{ $t('action.save') }}</large-button
        >
      </div>
    </ValidationObserver>

    <div class="group-members-row">
      <div class="row-title">{{ $t('accessRights.title') }}</div>
      <div class="row-buttons">
        <large-button
          :disabled="!hasServiceClients"
          outlined
          @click="removeAllServiceClients()"
          data-test="remove-subjects"
          >{{ $t('action.removeAll') }}</large-button
        >
        <large-button
          outlined
          class="add-members-button"
          @click="showAddServiceClientDialog()"
          data-test="show-add-subjects"
          >{{ $t('accessRights.addServiceClients') }}</large-button
        >
      </div>
    </div>

    <v-card flat>
      <table class="xrd-table group-members-table">
        <tr>
          <th>{{ $t('services.memberNameGroupDesc') }}</th>
          <th>{{ $t('services.idGroupCode') }}</th>
          <th>{{ $t('type') }}</th>
          <th>{{ $t('accessRights.rightsGiven') }}</th>
          <th></th>
        </tr>
        <template v-if="serviceClients">
          <tr v-for="sc in serviceClients" v-bind:key="sc.id">
            <td>{{ sc.name }}</td>
            <td>{{ sc.id }}</td>
            <td>{{ sc.service_client_type }}</td>
            <td>{{ sc.rights_given_at | formatDateTime }}</td>
            <td>
              <div class="button-wrap">
                <v-btn
                  small
                  outlined
                  rounded
                  color="primary"
                  class="xrd-small-button"
                  @click="removeServiceClient(sc)"
                  data-test="remove-subject"
                  >{{ $t('action.remove') }}</v-btn
                >
              </div>
            </td>
          </tr>
        </template>
      </table>

      <div class="footer-buttons-wrap">
        <large-button @click="close()" data-test="close">{{
          $t('action.close')
        }}</large-button>
      </div>
    </v-card>

    <!-- Confirm dialog remove Access Right service clients -->
    <confirmDialog
      :dialog="confirmMember"
      title="accessRights.removeTitle"
      text="accessRights.removeText"
      @cancel="confirmMember = false"
      @accept="doRemoveServiceClient()"
    />

    <!-- Confirm dialog remove all Access Right service clients -->
    <confirmDialog
      :dialog="confirmAllServiceClients"
      title="accessRights.removeAllTitle"
      text="accessRights.removeAllText"
      @cancel="confirmAllServiceClients = false"
      @accept="doRemoveAllServiveClient()"
    />

    <!-- Add access right service clients dialog -->
    <accessRightsDialog
      :dialog="addServiceClientDialogVisible"
      :existingServiceClients="serviceClients"
      :clientId="clientId"
      title="accessRights.addServiceClientsTitle"
      @cancel="closeAccessRightsDialog"
      @serviceClientsAdded="doAddServiceClient"
    />

    <!-- Warning dialog when service parameters are saved -->
    <warningDialog
      :dialog="warningDialog"
      :warnings="warningInfo"
      localizationParent="services.service_parameters_ssl_test_warnings"
      @cancel="cancelSubmit()"
      @accept="acceptWarnings()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import AccessRightsDialog from '../AccessRightsDialog.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import WarningDialog from '@/components/ui/WarningDialog.vue';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import { mapGetters } from 'vuex';
import { RouteName } from '@/global';
import { ServiceClient, ServiceClients, ServiceUpdate } from '@/openapi-types';
import { ServiceTypeEnum } from '@/domain';
import { encodePathParameter } from '@/util/api';

type NullableServiceClient = undefined | ServiceClient;

export default Vue.extend({
  components: {
    AccessRightsDialog,
    ConfirmDialog,
    HelpIcon,
    LargeButton,
    WarningDialog,
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    serviceId: {
      type: String,
      required: true,
    },
    clientId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      touched: false as boolean,
      confirmGroup: false as boolean,
      confirmMember: false as boolean,
      confirmAllServiceClients: false as boolean,
      selectedMember: undefined as NullableServiceClient,
      url: '' as string,
      addServiceClientDialogVisible: false as boolean,
      timeout: 23 as number,
      url_all: false as boolean,
      timeout_all: false as boolean,
      ssl_auth_all: false as boolean,
      warningInfo: [] as string[],
      warningDialog: false as boolean,
      saving: false as boolean,
    };
  },
  computed: {
    ...mapGetters(['service', 'serviceClients']),

    hasServiceClients(): boolean {
      return this.serviceClients?.length > 0;
    },
    disableSave(): boolean {
      // service is undefined --> can't save OR inputs are not touched
      return !this.service || !this.touched;
    },
    showApplyToAll(): boolean {
      return this.$route.query.descriptionType === ServiceTypeEnum.WSDL;
    },
  },

  methods: {
    cancelSubmit(): void {
      this.warningDialog = false;
    },
    acceptWarnings(): void {
      this.warningDialog = false;
      this.save(true);
    },
    save(ignoreWarnings: boolean): void {
      /**
       * For the current service backend returns ssl_auth as undefined if current service is using http.
       * If service is https then it can be either false or true. When saving service parameters however the ssl_auth
       * must be a boolean even if the service is using http. Backend will handle saving correct data.
       */
      const serviceUpdate: ServiceUpdate = {
        url: this.service.url,
        timeout: this.service.timeout,
        ssl_auth: this.service.ssl_auth ?? false, // set false as backup as backend takes boolean
        timeout_all: this.timeout_all,
        url_all: this.url_all,
        ssl_auth_all: this.ssl_auth_all,
        ignore_warnings: ignoreWarnings,
      };

      this.saving = true;
      api
        .patch(
          `/services/${encodePathParameter(this.serviceId)}`,
          serviceUpdate,
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'Service saved');
        })
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.warningDialog = true;
          } else {
            this.$store.dispatch('showError', error);
          }
        })
        .finally(() => (this.saving = false));
    },

    setTouched(): void {
      this.touched = true;
    },

    fetchData(serviceId: string): void {
      api
        .get(`/services/${encodePathParameter(serviceId)}/service-clients`)
        .then((res) => {
          this.$store.dispatch('setServiceClients', res.data);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },

    showAddServiceClientDialog(): void {
      this.addServiceClientDialogVisible = true;
    },

    doAddServiceClient(selected: ServiceClient[]): void {
      this.addServiceClientDialogVisible = false;

      api
        .post(
          `/services/${encodePathParameter(this.serviceId)}/service-clients`,
          {
            items: selected,
          } as ServiceClients,
        )
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            'accessRights.addServiceClientsSuccess',
          );
          this.fetchData(this.serviceId);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },

    closeAccessRightsDialog(): void {
      this.addServiceClientDialogVisible = false;
    },

    removeAllServiceClients(): void {
      this.confirmAllServiceClients = true;
    },

    doRemoveAllServiveClient(): void {
      const items = this.serviceClients.map((sc: ServiceClient) => ({
        id: sc.id,
        service_client_type: sc.service_client_type,
      }));

      this.removeServiceClients(items);
      this.confirmAllServiceClients = false;
    },
    removeServiceClient(member: NullableServiceClient): void {
      this.confirmMember = true;
      this.selectedMember = member;
    },
    doRemoveServiceClient() {
      const serviceClient: ServiceClient = this.selectedMember as ServiceClient;

      if (serviceClient.id) {
        this.removeServiceClients([serviceClient]);
      }

      this.confirmMember = false;
      this.selectedMember = undefined;
    },

    removeServiceClients(serviceClients: ServiceClient[]) {
      api
        .post(
          `/services/${encodePathParameter(
            this.serviceId,
          )}/service-clients/delete`,
          {
            items: serviceClients,
          },
        )
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            'accessRights.removeServiceClientsSuccess',
          );
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.$emit('updateService', this.service.id);
        });
    },
    close() {
      this.$router.push({
        name: RouteName.SubsystemServices,
        params: { id: this.clientId },
      });
    },
    isHttpsMethod(): boolean {
      return this.service.url.startsWith('https');
    },
    changeUrl(): void {
      this.setTouched();
      if (!this.isHttpsMethod() && this.service.ssl_auth === true) {
        this.service.ssl_auth = false;
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
@import '../../../assets/tables';

.apply-to-all {
  display: flex;
  justify-content: flex-end;

  .apply-to-all-text {
    width: 100px;
  }
}

.edit-row {
  display: flex;
  align-items: baseline;

  .description-input {
    width: 100%;
    max-width: 450px;
  }

  .edit-title {
    display: flex;
    align-content: center;
    min-width: 200px;
    margin-right: 20px;
  }

  .edit-input {
    display: flex;
    align-content: center;
    width: 100%;
  }

  & > .table-checkbox:last-child {
    width: 100px;
    max-width: 100px;
    min-width: 100px;
    margin-left: auto;
    margin-right: 0;
  }
}

.group-members-row {
  width: 100%;
  display: flex;
  margin-top: 70px;
  align-items: baseline;
}

.row-title {
  width: 100%;
  justify-content: space-between;
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.row-buttons {
  display: flex;
}

.add-members-button {
  margin-left: 20px;
}

.group-members-table {
  margin-top: 10px;
  width: 100%;

  th {
    text-align: left;
  }
}

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.footer-buttons-wrap {
  margin-top: 48px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid $XRoad-Grey40;
  padding-top: 20px;
}
</style>
