<template>
  <div class="xrd-tab-max-width">
    <div>
      <subViewTitle
        v-if="serviceDesc.type === serviceTypeEnum.WSDL"
        :title="$t('services.wsdlDetails')"
        @close="close"
      />
      <subViewTitle
        v-else-if="serviceDesc.type === serviceTypeEnum.REST"
        :title="$t('services.restDetails')"
        @close="close"
      />
      <subViewTitle
        v-else-if="serviceDesc.type === serviceTypeEnum.OPENAPI3"
        :title="$t('services.openapiDetails')"
        @close="close"
      />

      <div class="delete-wrap">
        <large-button
          v-if="showDelete"
          @click="showDeletePopup(serviceDesc.type)"
          outlined
          >{{ $t('action.delete') }}</large-button
        >
      </div>
    </div>

    <div class="edit-row">
      <div>{{ $t('services.serviceType') }}</div>

      <div class="code-input" v-if="serviceDesc.type === serviceTypeEnum.REST">
        {{ $t('services.restApiBasePath') }}
      </div>
      <div
        class="code-input"
        v-else-if="serviceDesc.type === serviceTypeEnum.OPENAPI3"
      >
        {{ $t('services.OpenApi3Description') }}
      </div>
      <div class="code-input" v-else>{{ $t('services.wsdlDescription') }}</div>
    </div>

    <ValidationObserver ref="form" v-slot="{ validate, invalid }">
      <div class="edit-row">
        <div>{{ $t('services.editUrl') }}</div>

        <ValidationProvider
          rules="required|wsdlUrl"
          name="url"
          v-slot="{ errors }"
          class="validation-provider"
        >
          <v-text-field
            v-model="serviceDesc.url"
            single-line
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
            serviceDesc.type === serviceTypeEnum.REST ||
              serviceDesc.type === serviceTypeEnum.OPENAPI3
          "
        >
          <div>{{ $t('services.serviceCode') }}</div>

          <ValidationProvider
            rules="required"
            name="code_field"
            v-slot="{ errors }"
            class="validation-provider"
          >
            <v-text-field
              v-model="currentServiceCode"
              single-line
              class="code-input"
              name="code_field"
              type="text"
              :maxlength="255"
              :error-messages="errors"
              @input="touched = true"
            ></v-text-field>
          </ValidationProvider>
        </template>
      </div>

      <v-card flat>
        <div class="footer-button-wrap">
          <large-button @click="close()" outlined>{{
            $t('action.cancel')
          }}</large-button>
          <large-button
            class="save-button"
            :loading="saveBusy"
            @click="save()"
            :disabled="!touched || invalid"
            >{{ $t('action.save') }}</large-button
          >
        </div>
      </v-card>
    </ValidationObserver>

    <!-- Confirm dialog delete WSDL -->
    <confirmDialog
      :dialog="confirmWSDLDelete"
      title="services.deleteTitle"
      text="services.deleteWsdlText"
      @cancel="confirmWSDLDelete = false"
      @accept="doDeleteServiceDesc()"
    />

    <!-- Confirm dialog delete REST -->
    <confirmDialog
      :dialog="confirmRESTDelete"
      title="services.deleteTitle"
      text="services.deleteRestText"
      @cancel="confirmRESTDelete = false"
      @accept="doDeleteServiceDesc()"
    />
    <!-- Confirm dialog for warnings when editing WSDL -->
    <warningDialog
      :dialog="confirmEditWarning"
      :warnings="warningInfo"
      :loading="editLoading"
      @cancel="cancelEditWarning()"
      @accept="acceptEditWarning()"
    ></warningDialog>
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
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import WarningDialog from '@/components/service/WarningDialog.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import { ServiceTypeEnum } from '@/domain';
import { ServiceDescription, ServiceDescriptionUpdate } from '@/openapi-types';

export default Vue.extend({
  components: {
    SubViewTitle,
    ConfirmDialog,
    WarningDialog,
    LargeButton,
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
      serviceTypeEnum: ServiceTypeEnum,
      editLoading: false as boolean,
      serviceDescriptionUpdate: null as ServiceDescriptionUpdate | null,
    };
  },
  computed: {
    showDelete(): boolean {
      return this.$store.getters.hasPermission(Permissions.DELETE_WSDL);
    },
  },
  methods: {
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
        this.serviceDescriptionUpdate.type === this.serviceTypeEnum.REST ||
        this.serviceDescriptionUpdate.type === this.serviceTypeEnum.OPENAPI3
      ) {
        this.serviceDescriptionUpdate.rest_service_code = this.initialServiceCode;
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
          this.$store.dispatch('showSuccess', 'localGroup.descSaved');
          this.saveBusy = false;
          this.serviceDescriptionUpdate = null;
          this.$router.go(-1);
        })
        .catch((error) => {
          if (error.response.data.warnings) {
            this.warningInfo = error.response.data.warnings;
            this.confirmEditWarning = true;
          } else {
            this.$store.dispatch('showError', error);
            this.saveBusy = false;
            this.serviceDescriptionUpdate = null;
          }
        });
    },

    fetchData(id: string): void {
      api
        .get<ServiceDescription>(`/service-descriptions/${id}`)
        .then((res) => {
          this.serviceDesc = res.data;
          this.initialServiceCode =
            this.serviceDesc.services &&
            this.serviceDesc.services[0] &&
            this.serviceDesc.services[0].service_code;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },

    showDeletePopup(serviceType: string): void {
      if (serviceType === this.serviceTypeEnum.WSDL) {
        this.confirmWSDLDelete = true;
      } else {
        this.confirmRESTDelete = true;
      }
    },
    doDeleteServiceDesc(): void {
      api
        .remove(`/service-descriptions/${this.id}`)
        .then(() => {
          this.$store.dispatch('showSuccess', 'services.deleted');
          this.confirmWSDLDelete = false;
          this.confirmRESTDelete = false;
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
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
          this.$store.dispatch('showSuccess', 'localGroup.descSaved');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
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
  created() {
    this.fetchData(this.id);
  },
  watch: {
    serviceDesc(desc: ServiceDescription) {
      if (desc.services?.[0]?.service_code) {
        this.currentServiceCode = desc.services[0].service_code;
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/dialogs';

.edit-row {
  display: flex;
  align-content: center;
  align-items: baseline;
  margin-top: 30px;

  > div {
    min-width: 90px;
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

.footer-button-wrap {
  margin-top: 48px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid $XRoad-Grey40;
  padding-top: 20px;
}

.save-button {
  margin-left: 20px;
}
</style>
