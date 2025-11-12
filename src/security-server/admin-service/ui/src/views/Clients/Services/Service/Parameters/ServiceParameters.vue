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
  <XrdFormBlock v-if="service" class="mb-6">
    <div v-if="showApplyToAll">
      <div class="text-end font-weight-medium body-regular">
        {{ $t('services.applyToAll') }}
      </div>
      <v-divider class="mt-4 mb-4" color="border" />
    </div>
    <XrdFormBlockRow description="services.urlTooltip" adjust-against-content>
      <v-text-field
        v-bind="serviceUrlRef"
        data-test="service-url-text-field"
        class="xrd"
        :disabled="!canEdit"
        :label="$t('services.serviceUrl')"
        @update:model-value="changeUrl()"
      />
      <template v-if="showApplyToAll" #append>
        <v-checkbox
          v-model="url_all"
          data-test="url-all"
          class="xrd"
          hide-details
          @update:model-value="setTouched()"
        />
      </template>
    </XrdFormBlockRow>

    <XrdFormBlockRow
      description="services.timeoutTooltip"
      adjust-against-content
    >
      <v-text-field
        v-bind="serviceTimeoutRef"
        data-test="service-timeout-text-field"
        name="serviceTimeout"
        type="number"
        class="xrd"
        :disabled="!canEdit"
        :label="$t('services.timeoutSec')"
        @update:model-value="setTouched()"
      />
      <template v-if="showApplyToAll" #append>
        <v-checkbox
          v-model="timeout_all"
          data-test="timeout-all"
          class="xrd"
          hide-details
          @update:model-value="setTouched()"
        />
      </template>
    </XrdFormBlockRow>

    <XrdFormBlockRow description="services.tlsTooltip" adjust-against-content>
      <v-checkbox
        v-model="service.ssl_auth"
        data-test="ssl-auth"
        class="xrd"
        :disabled="!isHttpsMethod() || !canEdit"
        :label="$t('services.verifyTls')"
        @update:model-value="setTouched()"
      />
      <template v-if="showApplyToAll" #append>
        <v-checkbox
          v-model="ssl_auth_all"
          data-test="ssl-auth-all"
          class="xrd"
          hide-details
          @update:model-value="setTouched()"
        />
      </template>
    </XrdFormBlockRow>
    <div v-if="canEdit" class="d-flex flex-row mt-6">
      <XrdBtn
        data-test="save-service-parameters"
        text="action.save"
        prepend-icon="check"
        class="ml-auto"
        :disabled="!meta.valid || disableSave"
        :loading="saving"
        @click="save(false)"
      />
    </div>
  </XrdFormBlock>

  <div class="d-flex flex-row align-center pt-4 mb-4">
    <div class="font-weight-medium title-component">
      {{ $t('accessRights.title') }}
    </div>
    <v-spacer />
    <XrdBtn
      v-if="canEdit"
      data-test="remove-subjects"
      class="mr-2"
      variant="outlined"
      text="action.removeAll"
      prepend-icon="do_not_disturb_on"
      :disabled="!hasServiceClients"
      @click="removeAllServiceClients()"
    />
    <XrdBtn
      v-if="canEdit"
      data-test="show-add-subjects"
      text="accessRights.addServiceClients"
      prepend-icon="add_circle"
      :loading="adding"
      @click="showAddServiceClientDialog()"
    />
  </div>

  <v-data-table
    data-test="access-rights-subjects"
    class="xrd xrd-rounded-12 border"
    hide-default-footer
    items-per-page="-1"
    :loading="loading"
    :headers="headers"
    :items="serviceClients"
  >
    <template #item.name="{ item }">
      <XrdLabelWithIcon icon="id_card" semi-bold>
        <template #label>
          <client-name :service-client="item" />
        </template>
      </XrdLabelWithIcon>
    </template>
    <template #item.rights_given_at="{ item }">
      <XrdDateTime :value="item.rights_given_at" />
    </template>
    <template #item.action="{ item }">
      <XrdBtn
        v-if="canEdit"
        data-test="remove-subject"
        variant="text"
        color="tertiary"
        text="action.remove"
        @click="removeServiceClient(item)"
      />
    </template>
  </v-data-table>

  <!-- Confirm dialog remove Access Right service clients -->
  <XrdConfirmDialog
    v-if="confirmMember"
    title="accessRights.removeTitle"
    text="accessRights.removeText"
    data-test="confirm-delete-access-right"
    :loading="deleting"
    @cancel="confirmMember = false"
    @accept="doRemoveServiceClient()"
  />

  <!-- Confirm dialog remove all Access Right service clients -->
  <xrdConfirmDialog
    v-if="confirmAllServiceClients"
    title="accessRights.removeAllTitle"
    text="accessRights.removeAllText"
    data-test="confirm-delete-all-access-right"
    :loading="deleting"
    @cancel="confirmAllServiceClients = false"
    @accept="doRemoveAllServiceClient()"
  />

  <!-- Add access right service clients dialog -->
  <AccessRightsDialog
    v-if="serviceDescription && addServiceClientDialogVisible"
    title="accessRights.addServiceClientsTitle"
    :existing-service-clients="serviceClients"
    :client-id="serviceDescription.client_id"
    @cancel="closeAccessRightsDialog"
    @service-clients-added="doAddServiceClient"
  />

  <!-- Warning dialog when service parameters are saved -->
  <WarningDialog
    v-if="warningDialog"
    :warnings="warningInfo"
    localization-parent="services.service_parameters_ssl_test_warnings"
    @cancel="cancelSubmit()"
    @accept="acceptWarnings()"
  />
</template>

<script lang="ts">
import { defineComponent, watch } from 'vue';
import AccessRightsDialog from '../AccessRightsDialog.vue';
import WarningDialog from '@/components/ui/WarningDialog.vue';
import { Permissions } from '@/global';
import {
  CodeWithDetails,
  ServiceClient,
  ServiceUpdate,
  ServiceType,
} from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useServices } from '@/store/modules/services';
import { PublicPathState, useForm } from 'vee-validate';
import ClientName from '@/components/client/ClientName.vue';
import {
  XrdDateTime,
  XrdFormBlock,
  XrdFormBlockRow,
  XrdBtn,
  XrdLabelWithIcon,
  useNotifications,
  XrdConfirmDialog,
} from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';

type NullableServiceClient = undefined | ServiceClient;

export default defineComponent({
  components: {
    ClientName,
    AccessRightsDialog,
    WarningDialog,
    XrdDateTime,
    XrdFormBlock,
    XrdFormBlockRow,
    XrdBtn,
    XrdLabelWithIcon,
    XrdConfirmDialog,
  },
  emits: ['update-service'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    const { meta, values, setValues, defineComponentBinds, resetForm } =
      useForm({
        validationSchema: {
          serviceUrl: 'required|max:255|wsdlUrl',
          serviceTimeout: 'required|between:0,1000',
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
    return {
      meta,
      values,
      setValues,
      resetForm,
      serviceUrlRef,
      serviceTimeoutRef,
      addError,
      addSuccessMessage,
    };
  },
  data() {
    return {
      loading: false as boolean,
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
      adding: false as boolean,
      deleting: false as boolean,
      deletingAll: false as boolean,
    };
  },
  computed: {
    ...mapState(useServices, ['service', 'serviceClients']),
    ...mapState(useServiceDescriptions, ['serviceDescription']),
    ...mapState(useUser, ['hasPermission']),
    hasServiceClients(): boolean {
      return this.serviceClients?.length > 0;
    },
    disableSave(): boolean {
      return !this.service || !this.touched;
    },
    showApplyToAll(): boolean {
      return (
        this.serviceDescription?.type === ServiceType.WSDL &&
        this.hasPermission(Permissions.EDIT_SERVICE_PARAMS)
      );
    },
    canEdit(): boolean {
      return this.hasPermission(Permissions.EDIT_SERVICE_PARAMS);
    },
    headers() {
      return [
        { title: this.$t('services.subsystemNameGroupDesc'), key: 'name' },
        { title: this.$t('services.idGroupCode'), key: 'id' },
        { title: this.$t('general.type'), key: 'service_client_type' },
        { title: this.$t('accessRights.rightsGiven'), key: 'rights_given_at' },
        { title: '', key: 'action' },
      ] as DataTableHeader[];
    },
  },
  watch: {
    service: {
      immediate: true,
      handler(newV) {
        if (newV) {
          this.fetchData(newV.id);
          this.resetForm({
            values: { serviceUrl: newV.url, serviceTimeout: newV.timeout },
          });
        }
      },
    },
  },
  methods: {
    ...mapActions(useServices, [
      'updateService',
      'fetchServiceClients',
      'addServiceClients',
      'removeServiceClients',
    ]),
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

      if (
        !this.service ||
        !this.values.serviceUrl ||
        this.values.serviceTimeout === undefined
      ) {
        return;
      }

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
      this.updateService(this.service.id, serviceUpdate)
        .then(() => this.addSuccessMessage('services.serviceSaved'))
        .then(() => (this.touched = false))
        .catch((error) => {
          if (error?.response?.data?.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.warningDialog = true;
          } else {
            this.addError(error);
          }
        })
        .finally(() => (this.saving = false));
    },

    setTouched(): void {
      this.touched = true;
    },

    fetchData(serviceId: string): void {
      this.loading = true;
      this.fetchServiceClients(serviceId)
        .catch((error) => this.addError(error))
        .finally(() => (this.loading = false));
    },

    showAddServiceClientDialog(): void {
      this.addServiceClientDialogVisible = true;
    },

    doAddServiceClient(selected: ServiceClient[]): void {
      if (!this.service) {
        return;
      }
      const serviceId = this.service.id;
      this.addServiceClientDialogVisible = false;
      this.adding = true;

      this.addServiceClients(this.service?.id, {
        items: selected,
      })
        .then(() => {
          this.addSuccessMessage('accessRights.addServiceClientsSuccess');
          this.fetchData(serviceId);
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.adding = false));
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

      this.doRemoveServiceClients(items).finally(
        () => (this.confirmAllServiceClients = false),
      );
    },
    removeServiceClient(member: NullableServiceClient): void {
      this.confirmMember = true;
      this.selectedMember = member;
    },
    doRemoveServiceClient() {
      const serviceClient: ServiceClient = this.selectedMember as ServiceClient;

      if (serviceClient.id) {
        this.deleting = true;
        this.doRemoveServiceClients([serviceClient])
          .finally(() => (this.confirmMember = false))
          .finally(() => (this.selectedMember = undefined));
      } else {
        this.confirmMember = false;
        this.selectedMember = undefined;
      }
    },

    async doRemoveServiceClients(serviceClients: ServiceClient[]) {
      if (!this.service) {
        return;
      }
      this.deleting = true;
      return this.removeServiceClients(this.service.id, {
        items: serviceClients,
      })
        .then(() => {
          this.addSuccessMessage('accessRights.removeSuccess');
        })
        .catch((error) => this.addError(error))
        .finally(() => {
          this.$emit('update-service', this.service?.id);
        })
        .finally(() => (this.deleting = false));
    },
    isHttpsMethod(): boolean {
      return this.service?.url?.startsWith('https') || false;
    },
    changeUrl(): void {
      this.setTouched();
      if (!this.isHttpsMethod() && this.service?.ssl_auth === true) {
        this.service.ssl_auth = false;
      }
    },
  },
});
</script>

<style lang="scss" scoped></style>
