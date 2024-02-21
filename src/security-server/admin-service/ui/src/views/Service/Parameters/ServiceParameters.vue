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
  <div class="">
    <div v-if="showApplyToAll" class="apply-to-all">
      <div class="apply-to-all-text">{{ $t('services.applyToAll') }}</div>
    </div>

    <div class="px-4 pt-4">
      <div class="edit-row">
        <xrd-form-label
          class="edit-title"
          data-test="service-parameters-service-url-label"
          :label-text="$t('services.serviceUrl')"
          :help-text="$t('services.urlTooltip')"
        />

        <div class="edit-input">
          <v-text-field
            v-bind="serviceUrlRef"
            variant="outlined"
            class="description-input"
            data-test="service-url-text-field"
            :disabled="!canEdit"
            @update:model-value="changeUrl()"
          ></v-text-field>
        </div>

        <v-checkbox
          v-if="showApplyToAll"
          v-model="url_all"
          class="table-checkbox"
          data-test="url-all"
          @update:model-value="setTouched()"
        ></v-checkbox>
      </div>

      <div class="edit-row">
        <xrd-form-label
          class="edit-title"
          data-test="service-parameters-timeout-label"
          :label-text="$t('services.timeoutSec')"
          :help-text="$t('services.timeoutTooltip')"
        />

        <div class="edit-input">
          <v-text-field
            v-bind="serviceTimeoutRef"
            variant="outlined"
            type="number"
            style="max-width: 200px"
            name="serviceTimeout"
            :disabled="!canEdit"
            data-test="service-timeout-text-field"
            @update:model-value="setTouched()"
          ></v-text-field>
        </div>

        <v-checkbox
          v-if="showApplyToAll"
          v-model="timeout_all"
          class="table-checkbox"
          data-test="timeout-all"
          @update:model-value="setTouched()"
        ></v-checkbox>
      </div>

      <div class="edit-row">
        <xrd-form-label
          class="edit-title"
          data-test="service-parameters-verify-tls-label"
          :label-text="$t('services.verifyTls')"
          :help-text="$t('services.tlsTooltip')"
        />

        <div class="edit-input">
          <v-checkbox
            v-model="service.ssl_auth"
            :disabled="!isHttpsMethod() || !canEdit"
            class="table-checkbox"
            data-test="ssl-auth"
            @update:model-value="setTouched()"
          ></v-checkbox>
        </div>

        <v-checkbox
          v-if="showApplyToAll"
          v-model="ssl_auth_all"
          class="table-checkbox"
          data-test="ssl-auth-all"
          @update:model-value="setTouched()"
        ></v-checkbox>
      </div>

      <div class="button-wrap">
        <xrd-button
          v-if="canEdit"
          :disabled="!meta.valid || disableSave"
          :loading="saving"
          data-test="save-service-parameters"
          @click="save(false)"
          >{{ $t('action.save') }}
        </xrd-button>
      </div>
    </div>

    <div class="group-members-row px-4">
      <div class="row-title">{{ $t('accessRights.title') }}</div>
      <div class="row-buttons">
        <xrd-button
          v-if="canEdit"
          :disabled="!hasServiceClients"
          outlined
          data-test="remove-subjects"
          @click="removeAllServiceClients()"
          >{{ $t('action.removeAll') }}
        </xrd-button>
        <xrd-button
          v-if="canEdit"
          outlined
          class="add-members-button"
          data-test="show-add-subjects"
          @click="showAddServiceClientDialog()"
          >{{ $t('accessRights.addServiceClients') }}
        </xrd-button>
      </div>
    </div>

    <v-card flat class="pa-0 ma-0">
      <table class="xrd-table group-members-table">
        <tr>
          <th>{{ $t('services.memberNameGroupDesc') }}</th>
          <th>{{ $t('services.idGroupCode') }}</th>
          <th>{{ $t('general.type') }}</th>
          <th>{{ $t('accessRights.rightsGiven') }}</th>
          <th></th>
        </tr>
        <template v-if="serviceClients">
          <tr v-for="sc in serviceClients" :key="sc.id">
            <td class="identifier-wrap">{{ sc.name }}</td>
            <td class="identifier-wrap">{{ sc.id }}</td>
            <td>{{ sc.service_client_type }}</td>
            <td>{{ $filters.formatDateTime(sc.rights_given_at ?? '') }}</td>
            <td>
              <div class="button-wrap">
                <xrd-button
                  v-if="canEdit"
                  text
                  :outlined="false"
                  data-test="remove-subject"
                  @click="removeServiceClient(sc)"
                  >{{ $t('action.remove') }}
                </xrd-button>
              </div>
            </td>
          </tr>
        </template>
      </table>

      <div class="xrd-footer-buttons-wrap">
        <xrd-button data-test="close" @click="close()"
          >{{ $t('action.close') }}
        </xrd-button>
      </div>
    </v-card>

    <!-- Confirm dialog remove Access Right service clients -->
    <xrd-confirm-dialog
      v-if="confirmMember"
      title="accessRights.removeTitle"
      text="accessRights.removeText"
      data-test="confirm-delete-access-right"
      @cancel="confirmMember = false"
      @accept="doRemoveServiceClient()"
    />

    <!-- Confirm dialog remove all Access Right service clients -->
    <xrd-confirm-dialog
      v-if="confirmAllServiceClients"
      title="accessRights.removeAllTitle"
      text="accessRights.removeAllText"
      data-test="confirm-delete-all-access-right"
      @cancel="confirmAllServiceClients = false"
      @accept="doRemoveAllServiceClient()"
    />

    <!-- Add access right service clients dialog -->
    <AccessRightsDialog
      v-if="addServiceClientDialogVisible"
      :dialog="addServiceClientDialogVisible"
      :existing-service-clients="serviceClients"
      :client-id="clientId"
      :title="$t('accessRights.addServiceClientsTitle')"
      @cancel="closeAccessRightsDialog"
      @service-clients-added="doAddServiceClient"
    />

    <!-- Warning dialog when service parameters are saved -->
    <WarningDialog
      :dialog="warningDialog"
      :warnings="warningInfo"
      localization-parent="services.service_parameters_ssl_test_warnings"
      @cancel="cancelSubmit()"
      @accept="acceptWarnings()"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import * as api from '@/util/api';
import AccessRightsDialog from '../AccessRightsDialog.vue';
import WarningDialog from '@/components/ui/WarningDialog.vue';
import { RouteName, Permissions } from '@/global';
import {
  CodeWithDetails,
  Service,
  ServiceClient,
  ServiceClients,
  ServiceUpdate,
} from '@/openapi-types';
import { ServiceTypeEnum } from '@/domain';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { useServices } from '@/store/modules/services';
import { PublicPathState, useForm } from 'vee-validate';

type NullableServiceClient = undefined | ServiceClient;

export default defineComponent({
  components: {
    AccessRightsDialog,
    WarningDialog,
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
  emits: ['update-service'],
  setup() {
    const { service } = useServices();
    const { meta, values, setValues, defineComponentBinds } = useForm({
      validationSchema: {
        serviceUrl: 'required|max:255|wsdlUrl',
        serviceTimeout: 'required|between:0,1000',
      },
      initialValues: {
        serviceUrl: service.url,
        serviceTimeout: service.timeout,
      },
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const serviceUrlRef = defineComponentBinds('serviceUrl', componentConfig);
    const serviceTimeoutRef = defineComponentBinds(
      'serviceTimeout',
      componentConfig,
    );
    return { meta, values, setValues, serviceUrlRef, serviceTimeoutRef };
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
      warningInfo: [] as CodeWithDetails[],
      warningDialog: false as boolean,
      saving: false as boolean,
    };
  },
  computed: {
    ...mapState(useServices, ['service', 'serviceClients']),
    ...mapState(useUser, ['hasPermission']),
    hasServiceClients(): boolean {
      return this.serviceClients?.length > 0;
    },
    disableSave(): boolean {
      return !this.service || !this.touched;
    },
    showApplyToAll(): boolean {
      return (
        this.$route.query.descriptionType === ServiceTypeEnum.WSDL &&
        this.hasPermission(Permissions.EDIT_SERVICE_PARAMS)
      );
    },
    canEdit(): boolean {
      return this.hasPermission(Permissions.EDIT_SERVICE_PARAMS);
    },
  },
  watch: {
    service(newValue: Service) {
      this.setValues({
        serviceUrl: newValue.url,
        serviceTimeout: newValue.timeout,
      });
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useServices, ['setService', 'setServiceClients']),
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
        url: this.values.serviceUrl,
        timeout: this.values.serviceTimeout,
        ssl_auth: this.service.ssl_auth ?? false, // set false as backup as backend takes boolean
        timeout_all: this.timeout_all,
        url_all: this.url_all,
        ssl_auth_all: this.ssl_auth_all,
        ignore_warnings: ignoreWarnings,
      };

      this.saving = true;
      api
        .patch<Service>(
          `/services/${encodePathParameter(this.serviceId)}`,
          serviceUpdate,
        )
        .then((res) => {
          this.setService(res.data);
          this.showSuccess(this.$t('services.serviceSaved'));
        })
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.warningDialog = true;
          } else {
            this.showError(error);
          }
        })
        .finally(() => (this.saving = false));
    },

    setTouched(): void {
      this.touched = true;
    },

    fetchData(serviceId: string): void {
      api
        .get<ServiceClient[]>(
          `/services/${encodePathParameter(serviceId)}/service-clients`,
        )
        .then((res) => {
          this.setServiceClients(res.data);
        })
        .catch((error) => {
          this.showError(error);
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
          this.showSuccess(this.$t('accessRights.addServiceClientsSuccess'));
          this.fetchData(this.serviceId);
        })
        .catch((error) => {
          this.showError(error);
        });
    },

    closeAccessRightsDialog(): void {
      this.addServiceClientDialogVisible = false;
    },

    removeAllServiceClients(): void {
      this.confirmAllServiceClients = true;
    },

    doRemoveAllServiceClient(): void {
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
          this.showSuccess(this.$t('accessRights.removeSuccess'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.$emit('update-service', this.service.id);
        });
    },
    close() {
      this.$router.push({
        name: RouteName.SubsystemServices,
        params: { id: this.clientId },
      });
    },
    isHttpsMethod(): boolean {
      return this.service?.url?.startsWith('https');
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
@import '@/assets/tables';

.apply-to-all {
  display: flex;
  justify-content: flex-end;

  .apply-to-all-text {
    width: 100px;
  }
}

.edit-row {
  display: flex;

  .description-input {
    width: 100%;
    max-width: 450px;
  }

  .edit-title {
    display: flex;
    align-content: center;
    min-width: 200px;
    max-width: 400px;
    margin-right: 20px;
  }

  .edit-input {
    display: flex;
    align-content: center;
    width: 100%;
    max-width: 400px;
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
  color: $XRoad-Black100;
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
</style>
