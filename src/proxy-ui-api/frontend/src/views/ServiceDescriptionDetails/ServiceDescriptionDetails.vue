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
        v-if="serviceDesc.type === serviceType.WSDL"
        :title="$t('services.wsdlDetails')"
        data-test="wsdl-service-description-details-dialog"
        @close="close"
      />
      <xrd-sub-view-title
        v-else-if="serviceDesc.type === serviceType.REST"
        :title="$t('services.restDetails')"
        data-test="rest-service-description-details-dialog"
        @close="close"
      />
      <xrd-sub-view-title
        v-else-if="serviceDesc.type === serviceType.OPENAPI3"
        :title="$t('services.openapiDetails')"
        data-test="openapi-service-description-details-dialog"
        @close="close"
      />

      <div class="delete-wrap">
        <xrd-button
          v-if="showDelete"
          data-test="service-description-details-delete-button"
          outlined
          @click="showDeletePopup(serviceDesc.type)"
          >{{ $t('action.delete') }}</xrd-button
        >
      </div>
    </div>

    <ValidationObserver ref="form" v-slot="{ invalid }">
      <div class="px-4">
        <div class="edit-row pb-4">
          <div>{{ $t('services.serviceType') }}</div>

          <div
            v-if="serviceDesc.type === serviceType.REST"
            class="code-input"
            data-test="service-description-details-url-type-value"
          >
            {{ $t('services.restApiBasePath') }}
          </div>
          <div
            v-else-if="serviceDesc.type === serviceType.OPENAPI3"
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

          <ValidationProvider
            v-slot="{ errors }"
            rules="required|wsdlUrl"
            name="url"
            class="validation-provider"
          >
            <v-text-field
              v-model="serviceDesc.url"
              outlined
              class="url-input"
              name="url"
              :error-messages="errors"
              type="text"
              @input="touched = true"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="edit-row">
          <template
            v-if="
              serviceDesc.type === serviceType.REST ||
              serviceDesc.type === serviceType.OPENAPI3
            "
          >
            <div>{{ $t('services.serviceCode') }}</div>

            <ValidationProvider
              v-slot="{ errors }"
              rules="required|xrdIdentifier"
              name="code_field"
              class="validation-provider"
            >
              <v-text-field
                v-model="currentServiceCode"
                class="code-input"
                name="code_field"
                outlined
                type="text"
                :maxlength="255"
                :error-messages="errors"
                @input="touched = true"
              ></v-text-field>
            </ValidationProvider>
          </template>
        </div>
      </div>
      <v-card flat>
        <div class="dtlv-actions-footer mt-12">
          <xrd-button
            data-test="service-description-details-cancel-button"
            outlined
            @click="close()"
            >{{ $t('action.cancel') }}</xrd-button
          >
          <xrd-button
            :loading="saveBusy"
            data-test="service-description-details-save-button"
            :disabled="!touched || invalid"
            @click="save()"
            >{{ $t('action.save') }}</xrd-button
          >
        </div>
      </v-card>
    </ValidationObserver>

    <!-- Confirm dialog delete WSDL -->
    <xrd-confirm-dialog
      :dialog="confirmWSDLDelete"
      title="services.deleteTitle"
      text="services.deleteWsdlText"
      @cancel="confirmWSDLDelete = false"
      @accept="doDeleteServiceDesc()"
    />

    <!-- Confirm dialog delete REST -->
    <xrd-confirm-dialog
      :dialog="confirmRESTDelete"
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
import Vue from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { Permissions } from '@/global';
import * as api from '@/util/api';
import ServiceWarningDialog from '@/components/service/ServiceWarningDialog.vue';
import {
  ServiceDescription,
  ServiceDescriptionUpdate,
  ServiceType,
} from '@/openapi-types';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';

export default Vue.extend({
  components: {
    ServiceWarningDialog,
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      confirmWSDLDelete: false,
      confirmRESTDelete: false,
      confirmEditWarning: false,
      warningInfo: [],
      touched: false,
      serviceDesc: {} as ServiceDescription,
      currentServiceCode: undefined as string | undefined,
      initialServiceCode: '',
      saveBusy: false,
      serviceType: ServiceType,
      editLoading: false as boolean,
      serviceDescriptionUpdate: null as ServiceDescriptionUpdate | null,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    showDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_WSDL);
    },
  },
  watch: {
    serviceDesc(desc: ServiceDescription) {
      if (desc.services?.[0]?.service_code) {
        this.currentServiceCode = desc.services[0].service_code;
      }
    },
  },
  created() {
    this.fetchData(this.id);
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    close(): void {
      this.$router.go(-1);
    },

    save(): void {
      this.saveBusy = true;

      this.serviceDescriptionUpdate = {
        url: this.serviceDesc.url,
        type: this.serviceDesc.type,
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
          this.currentServiceCode
            ? this.currentServiceCode
            : this.serviceDescriptionUpdate.rest_service_code;
      }

      api
        .patch(
          `/service-descriptions/${this.id}`,
          this.serviceDescriptionUpdate,
        )
        .then(() => {
          this.showSuccess(this.$t('localGroup.descSaved'));
          this.saveBusy = false;
          this.serviceDescriptionUpdate = null;
          this.$router.go(-1);
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.confirmEditWarning = true;
          } else {
            this.showError(error);
            this.saveBusy = false;
            this.serviceDescriptionUpdate = null;
          }
        });
    },

    fetchData(id: string): void {
      api
        .get<ServiceDescription>(
          `/service-descriptions/${encodePathParameter(id)}`,
        )
        .then((res) => {
          this.serviceDesc = res.data;
          this.initialServiceCode =
            this.serviceDesc.services &&
            this.serviceDesc.services[0] &&
            this.serviceDesc.services[0].service_code;
        })
        .catch((error) => {
          this.showError(error);
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
          this.$router.go(-1);
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
          this.$router.go(-1);
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.saveBusy = false;
          this.editLoading = false;
          this.confirmEditWarning = false;
          this.serviceDescriptionUpdate = null;
        });
    },

    cancelEditWarning(): void {
      this.confirmEditWarning = false;
      this.saveBusy = false;
      this.editLoading = false;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/detail-views';

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
