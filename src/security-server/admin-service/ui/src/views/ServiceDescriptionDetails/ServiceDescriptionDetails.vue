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
  <div
    class="xrd-tab-max-width main-wrap"
    data-test="service-description-details-dialog"
  >
    <div class="pa-4">
      <xrd-sub-view-title
        v-if="serviceDescription.type === serviceType.WSDL"
        :title="$t('services.wsdlDetails')"
        data-test="wsdl-service-description-details-dialog"
        @close="close"
      />
      <xrd-sub-view-title
        v-else-if="serviceDescription.type === serviceType.REST"
        :title="$t('services.restDetails')"
        data-test="rest-service-description-details-dialog"
        @close="close"
      />
      <xrd-sub-view-title
        v-else-if="serviceDescription.type === serviceType.OPENAPI3"
        :title="$t('services.openapiDetails')"
        data-test="openapi-service-description-details-dialog"
        @close="close"
      />

      <div class="delete-wrap">
        <xrd-button
          v-if="showDelete"
          data-test="service-description-details-delete-button"
          outlined
          @click="showDeletePopup(serviceDescription.type)"
          >{{ $t('action.delete') }}
        </xrd-button>
      </div>
    </div>

    <div class="px-4">
      <div class="edit-row pb-4">
        <div>{{ $t('services.serviceType') }}</div>

        <div
          v-if="serviceDescription.type === serviceType.REST"
          class="code-input"
          data-test="service-description-details-url-type-value"
        >
          {{ $t('services.restApiBasePath') }}
        </div>
        <div
          v-else-if="serviceDescription.type === serviceType.OPENAPI3"
          class="code-input"
          data-test="service-description-details-url-type-value"
        >
          {{ $t('services.OpenApi3Description') }}
        </div>
        <div
          v-else
          class="code-input"
          data-test="service-description-details-url-type-value"
        >
          {{ $t('services.wsdlDescription') }}
        </div>
      </div>

      <div class="edit-row">
        <div>{{ $t('services.editUrl') }}</div>
        <v-text-field
          v-bind="serviceUrlRef"
          variant="outlined"
          class="url-input"
          type="text"
          data-test="service-url-text-field"
        ></v-text-field>
      </div>

      <div class="edit-row">
        <template
          v-if="
            serviceDescription.type === serviceType.REST ||
            serviceDescription.type === serviceType.OPENAPI3
          "
        >
          <div>{{ $t('services.serviceCode') }}</div>
          <v-text-field
            v-bind="serviceCodeRef"
            class="code-input"
            variant="outlined"
            type="text"
            :maxlength="255"
            data-test="service-code-text-field"
          ></v-text-field>
        </template>
      </div>
    </div>
    <v-card flat>
      <div class="detail-view-actions-footer mt-12">
        <xrd-button
          data-test="service-description-details-cancel-button"
          outlined
          @click="close()"
          >{{ $t('action.cancel') }}
        </xrd-button>
        <xrd-button
          :loading="saving"
          data-test="service-description-details-save-button"
          :disabled="!meta.dirty || !meta.valid"
          @click="save()"
          >{{ $t('action.save') }}
        </xrd-button>
      </div>
    </v-card>

    <!-- Confirm dialog delete WSDL -->
    <xrd-confirm-dialog
      v-if="confirmWSDLDelete"
      title="services.deleteTitle"
      text="services.deleteWsdlText"
      @cancel="confirmWSDLDelete = false"
      @accept="doDeleteServiceDesc()"
    />

    <!-- Confirm dialog delete REST -->
    <xrd-confirm-dialog
      v-if="confirmRESTDelete"
      title="services.deleteTitle"
      text="services.deleteRestText"
      @cancel="confirmRESTDelete = false"
      @accept="doDeleteServiceDesc()"
    />
    <!-- Confirm dialog for warnings when editing WSDL -->
    <ServiceWarningDialog
      :dialog="confirmEditWarning"
      :warnings="warningInfo"
      :loading="editLoading"
      @cancel="cancelEditWarning()"
      @accept="acceptEditWarning()"
    ></ServiceWarningDialog>
  </div>
</template>

<script lang="ts">
/***
 * Component for showing the details of REST or WSDL service description.
 * Both use the same api.
 */
import { defineComponent, ref } from 'vue';
import { Permissions } from '@/global';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import ServiceWarningDialog from '@/components/service/ServiceWarningDialog.vue';
import {
  ServiceDescription,
  ServiceDescriptionUpdate,
  ServiceType,
} from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { defineRule, PublicPathState, useForm } from 'vee-validate';
import { useServices } from '@/store/modules/services';
import i18n from '@/plugins/i18n';
import { FieldValidationMetaInfo } from '@vee-validate/i18n';

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
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    const { serviceDescriptions } = useServices();
    const serviceDescription: ServiceDescription = ref(
      serviceDescriptions.find((sd) => sd.id === props.id)!,
    ).value;
    const { meta, values, setFieldValue, defineComponentBinds } = useForm({
      validationSchema: {
        serviceUrl: 'required|wsdlUrl',
        serviceCode: {
          requiredIfREST: [serviceDescription?.type],
          xrdIdentifier: true,
        },
      },
      initialValues: {
        serviceUrl: serviceDescription.url,
        serviceCode: serviceDescription.services[0].service_code,
      },
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const serviceUrlRef = defineComponentBinds('serviceUrl', componentConfig);
    const serviceCodeRef = defineComponentBinds('serviceCode', componentConfig);
    return {
      meta,
      values,
      setFieldValue,
      serviceDescription,
      serviceUrlRef,
      serviceCodeRef,
    };
  },
  data() {
    return {
      confirmWSDLDelete: false,
      confirmRESTDelete: false,
      confirmEditWarning: false,
      warningInfo: [],
      initialServiceCode: '',
      saving: false,
      editLoading: false,
      serviceType: ServiceType,
      serviceDescriptionUpdate: null as ServiceDescriptionUpdate | null,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    showDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_WSDL);
    },
  },
  created() {
    this.initialServiceCode =
      this.serviceDescription.services?.[0]?.service_code;
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    close(): void {
      this.$router.back();
    },

    save(): void {
      this.saving = true;

      this.serviceDescriptionUpdate = {
        url: this.values.serviceUrl,
        type: this.serviceDescription.type,
        ignore_warnings: false,
      };

      if (
        this.serviceDescriptionUpdate.type === this.serviceType.REST ||
        this.serviceDescriptionUpdate.type === this.serviceType.OPENAPI3
      ) {
        this.serviceDescriptionUpdate.rest_service_code =
          this.initialServiceCode;
        this.serviceDescriptionUpdate.new_rest_service_code =
          this.serviceDescriptionUpdate.rest_service_code !==
          this.values.serviceCode
            ? this.values.serviceCode
            : this.serviceDescriptionUpdate.rest_service_code;
      }

      api
        .patch(
          `/service-descriptions/${this.id}`,
          this.serviceDescriptionUpdate,
        )
        .then(() => {
          this.showSuccess(this.$t('localGroup.descSaved'));
          this.saving = false;
          this.serviceDescriptionUpdate = null;
          this.$router.back();
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.confirmEditWarning = true;
          } else {
            this.showError(error);
            this.saving = false;
            this.serviceDescriptionUpdate = null;
          }
        });
    },

    showDeletePopup(serviceType: string): void {
      if (serviceType === this.serviceType.WSDL) {
        this.confirmWSDLDelete = true;
      } else {
        this.confirmRESTDelete = true;
      }
    },
    doDeleteServiceDesc(): void {
      api
        .remove(`/service-descriptions/${encodePathParameter(this.id)}`)
        .then(() => {
          this.showSuccess(this.$t('services.deleted'));
          this.confirmWSDLDelete = false;
          this.confirmRESTDelete = false;
          this.$router.back();
        })
        .catch((error) => {
          this.showError(error);
        });
    },

    acceptEditWarning(): void {
      this.editLoading = true;

      if (this.serviceDescriptionUpdate) {
        this.serviceDescriptionUpdate.ignore_warnings = true;
      }

      api
        .patch(
          `/service-descriptions/${this.id}`,
          this.serviceDescriptionUpdate,
        )
        .then(() => {
          this.showSuccess(this.$t('localGroup.descSaved'));
          this.$router.back();
        })
        .catch((error) => {
          this.showError(error);
        })
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

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/detail-views';

.main-wrap {
  background-color: white;
  margin-top: 20px;
  border-radius: 4px;
  box-shadow: $XRoad-DefaultShadow;
}

.edit-row {
  display: flex;
  align-content: center;
  align-items: flex-start;
  margin-top: 30px;

  > div {
    min-width: 120px;
  }

  .code-input {
    margin-left: 60px;
  }

  .url-input {
    margin-left: 60px;
  }
}

.delete-wrap {
  margin-top: 50px;
  display: flex;
  justify-content: flex-end;
}
</style>
