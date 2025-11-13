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
  <XrdElevatedViewFixedWidth
    :title="title"
    data-test="service-description-details-dialog"
    go-back-on-close
    :breadcrumbs="breadcrumbs"
  >
    <template #footer>
      <v-spacer />
      <XrdBtn
        v-if="canDelete"
        data-test="service-description-details-delete-button"
        variant="outlined"
        text="action.delete"
        prepend-icon="delete_forever"
        @click="confirmDelete = true"
      />
      <XrdBtn
        :loading="saving"
        data-test="service-description-details-save-button"
        class="ml-2"
        text="action.save"
        prepend-icon="check"
        :disabled="!meta.dirty || !meta.valid"
        @click="save()"
      />
    </template>

    <XrdFormBlock>
      <XrdFormBlockRow>
        <v-text-field
          data-test="selected-member-name"
          class="xrd"
          variant="plain"
          readonly
          hide-details
          :model-value="$t(serviceDescriptionType)"
          :label="$t('services.serviceType')"
        />
      </XrdFormBlockRow>
      <XrdFormBlockRow full-length>
        <v-text-field
          v-model="serviceUrl"
          v-bind="serviceUrlAttr"
          data-test="service-url-text-field"
          class="xrd"
          :label="$t('services.editUrl')"
        />
      </XrdFormBlockRow>
      <XrdFormBlockRow
        v-if="
          serviceDescription?.type === serviceType.REST ||
          serviceDescription?.type === serviceType.OPENAPI3
        "
        full-length
      >
        <v-text-field
          v-model="serviceCode"
          v-bind="serviceCodeAttr"
          data-test="service-code-text-field"
          class="xrd"
          :maxlength="255"
          :label="$t('services.serviceCode')"
        />
      </XrdFormBlockRow>
    </XrdFormBlock>

    <XrdConfirmDialog
      v-if="confirmDelete"
      title="services.deleteTitle"
      :text="deletePopupText"
      :loading="deleting"
      @cancel="confirmDelete = false"
      @accept="doDeleteServiceDesc"
    />

    <!-- Confirm dialog for warnings when editing WSDL -->
    <ServiceWarningDialog
      v-if="confirmEditWarning"
      :warnings="warningInfo"
      :loading="editLoading"
      @cancel="cancelEditWarning()"
      @accept="acceptEditWarning()"
    ></ServiceWarningDialog>
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts">
/***
 * Component for showing the details of REST or WSDL service description.
 * Both use the same api.
 */
import { defineComponent, ref, computed } from 'vue';
import { Permissions, RouteName } from '@/global';
import ServiceWarningDialog from '@/components/service/ServiceWarningDialog.vue';
import {
  ServiceDescription,
  ServiceDescriptionUpdate,
  ServiceType,
} from '@/openapi-types';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { defineRule, useForm } from 'vee-validate';
import {
  i18n,
  XrdElevatedViewFixedWidth,
  XrdBtn,
  XrdFormBlock,
  XrdFormBlockRow,
  useNotifications,
  XrdConfirmDialog, veeDefaultFieldConfig,
} from '@niis/shared-ui';
import { FieldValidationMetaInfo } from '@vee-validate/i18n';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';
import { useClient } from '@/store/modules/client';
import { clientTitle } from '@/util/ClientUtil';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';

defineRule(
  'requiredIfREST',
  (
    value: string,
    [expectedValue]: [string],
    ctx: FieldValidationMetaInfo,
  ): string | boolean => {
    if (
      (expectedValue === ServiceType.REST ||
        expectedValue === ServiceType.OPENAPI3) &&
      (!value || !value.length)
    ) {
      return i18n.global.t('customValidation.requiredIf', {
        fieldName: i18n.global.t(`fields.${ctx.field}`),
      });
    }
    return true;
  },
);

export default defineComponent({
  components: {
    ServiceWarningDialog,
    XrdElevatedViewFixedWidth,
    XrdBtn,
    XrdFormBlock,
    XrdFormBlockRow,
    XrdConfirmDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    const { addError, addSuccessMessage } = useNotifications();
    const clientStore = useClient();
    const {
      fetchServiceDescription,
      updateServiceDescription,
      deleterServiceDescription,
    } = useServiceDescriptions();
    const serviceDescription = ref<ServiceDescription | undefined>(undefined);
    const { meta, values, defineField, resetForm } = useForm({
      validationSchema: computed(() => ({
        serviceUrl: 'required|wsdlUrl',
        serviceCode: {
          requiredIfREST: [serviceDescription.value?.type],
          xrdIdentifier: true,
        },
      })),
      initialValues: {
        serviceUrl: '',
        serviceCode: '',
      },
    });

    const [serviceUrl, serviceUrlAttr] = defineField(
      'serviceUrl',
      veeDefaultFieldConfig(),
    );
    const [serviceCode, serviceCodeAttr] = defineField(
      'serviceCode',
      veeDefaultFieldConfig(),
    );
    return {
      meta,
      values,
      resetForm,
      serviceDescription,
      clientStore,
      serviceUrl,
      serviceUrlAttr,
      serviceCode,
      serviceCodeAttr,
      addError,
      addSuccessMessage,
      updateServiceDescription,
      deleterServiceDescription,
      fetchServiceDescription,
    };
  },
  data() {
    return {
      confirmDelete: false,
      confirmEditWarning: false,
      warningInfo: [],
      saving: false,
      deleting: false,
      editLoading: false,
      serviceType: ServiceType,
      serviceDescriptionUpdate: null as ServiceDescriptionUpdate | null,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    canDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_WSDL);
    },
    deletePopupText(): string {
      return this.serviceDescription?.type === ServiceType.WSDL
        ? 'services.deleteWsdlText'
        : 'services.deleteRestText';
    },
    title() {
      switch (this.serviceDescription?.type) {
        case ServiceType.WSDL:
          return 'services.wsdlDetails';
        case ServiceType.REST:
          return 'services.restDetails';
        case ServiceType.OPENAPI3:
          return 'services.openapiDetails';
        default:
          return undefined;
      }
    },
    serviceDescriptionType() {
      switch (this.serviceDescription?.type) {
        case ServiceType.REST:
          return 'services.restApiBasePath';
        case ServiceType.OPENAPI3:
          return 'services.OpenApi3Description';
        default:
          return 'services.wsdlDescription';
      }
    },
    breadcrumbs() {
      const breadcrumbs = [
        {
          title: this.$t('tab.main.clients'),
          to: { name: RouteName.Clients },
        },
      ] as BreadcrumbItem[];

      if (this.clientStore.client) {
        breadcrumbs.push(
          {
            title: clientTitle(
              this.clientStore.client,
              this.clientStore.clientLoading,
            ),
            to: {
              name: RouteName.SubsystemDetails,
              params: { id: this.clientStore.client.id },
            },
          },
          {
            title: this.$t('tab.client.services'),
            to: {
              name: RouteName.SubsystemServices,
              params: { id: this.clientStore.client.id },
            },
          },
        );
      }

      breadcrumbs.push({
        title: this.$t(
          this.serviceDescription?.type === ServiceType.WSDL
            ? 'services.editWsdl'
            : 'services.editRest',
        ),
      });
      return breadcrumbs;
    },
  },
  watch: {
    id: {
      immediate: true,
      handler() {
        this.fetchServiceDescription(this.id)
          .then((serviceDescription) => this.updateForm(serviceDescription))
          .then((serviceDescription) =>
            this.clientStore.fetchClient(serviceDescription.client_id),
          )
          .catch((error) => this.addError(error, { navigate: true }));
      },
    },
  },
  methods: {
    updateForm(serviceDescription: ServiceDescription) {
      this.serviceDescription = serviceDescription;
      this.resetForm({
        values: {
          serviceUrl: serviceDescription.url,
          serviceCode: serviceDescription.services[0].service_code,
        },
      });
      return serviceDescription;
    },
    close(): void {
      this.$router.back();
    },

    save(): void {
      if (!this.serviceDescription) {
        return;
      }
      this.saving = true;

      this.serviceDescriptionUpdate = {
        url: this.values.serviceUrl,
        type: this.serviceDescription?.type,
        ignore_warnings: false,
      };

      if (
        this.serviceDescriptionUpdate.type === this.serviceType.REST ||
        this.serviceDescriptionUpdate.type === this.serviceType.OPENAPI3
      ) {
        this.serviceDescriptionUpdate.rest_service_code =
          this.serviceDescription.services[0].service_code;

        this.serviceDescriptionUpdate.new_rest_service_code =
          this.serviceDescriptionUpdate.rest_service_code !==
          this.values.serviceCode
            ? this.values.serviceCode
            : this.serviceDescriptionUpdate.rest_service_code;
      }

      this.updateServiceDescription(this.id, this.serviceDescriptionUpdate)
        .then(() => {
          this.addSuccessMessage('localGroup.descSaved', {}, true);
          this.saving = false;
          this.serviceDescriptionUpdate = null;
          this.$router.back();
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.confirmEditWarning = true;
          } else {
            this.addError(error);
            this.saving = false;
            this.serviceDescriptionUpdate = null;
          }
        });
    },
    doDeleteServiceDesc(): void {
      this.deleting = true;
      this.deleterServiceDescription(this.id)
        .then(() => {
          this.addSuccessMessage('services.deleted', {}, true);
          this.confirmDelete = false;
          this.$router.back();
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.deleting = false));
    },

    acceptEditWarning(): void {
      if (this.serviceDescriptionUpdate) {
        this.serviceDescriptionUpdate.ignore_warnings = true;
      } else {
        return;
      }
      this.editLoading = true;

      this.updateServiceDescription(this.id, this.serviceDescriptionUpdate)
        .then(() => {
          this.addSuccessMessage('localGroup.descSaved', {}, true);
          this.$router.back();
        })
        .catch((error) => this.addError(error))
        .finally(() => {
          this.saving = false;
          this.editLoading = false;
          this.confirmEditWarning = false;
          this.serviceDescriptionUpdate = null;
        });
    },

    cancelEditWarning(): void {
      this.confirmEditWarning = false;
      this.saving = false;
      this.editLoading = false;
    },
  },
});
</script>

<style lang="scss" scoped></style>
