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
              @input="setTouched()"
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
              style="max-width: 200px;"
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
            :disabled="!isHttps"
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
          @click="save()"
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
      title="localGroup.removeTitle"
      text="localGroup.removeText"
      @cancel="confirmMember = false"
      @accept="doRemoveServiceClient()"
    />

    <!-- Confirm dialog remove all Access Right service clients -->
    <confirmDialog
      :dialog="confirmAllServiceClients"
      title="localGroup.removeAllTitle"
      text="localGroup.removeAllText"
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
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import AccessRightsDialog from '../AccessRightsDialog.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import { mapGetters } from 'vuex';
import { RouteName } from '@/global';
import { ServiceClient } from '@/openapi-types';
import { ServiceTypeEnum } from '@/domain';

type NullableServiceClient = undefined | ServiceClient;

export default Vue.extend({
  components: {
    AccessRightsDialog,
    ConfirmDialog,
    HelpIcon,
    LargeButton,
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
      touched: false,
      confirmGroup: false,
      confirmMember: false,
      confirmAllServiceClients: false,
      selectedMember: undefined as NullableServiceClient,
      description: undefined,
      url: '',
      addServiceClientDialogVisible: false,
      timeout: 23,
      url_all: false,
      timeout_all: false,
      ssl_auth_all: false,
    };
  },
  computed: {
    ...mapGetters(['service', 'serviceClients']),
    isHttps(): boolean {
      return this.service.url.startsWith('https');
    },
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
    save(): void {
      api
        .patch(`/services/${this.serviceId}`, {
          service: this.service,
          timeout_all: this.timeout_all,
          url_all: this.url_all,
          ssl_auth_all: this.ssl_auth_all,
        })
        .then(() => {
          this.$store.dispatch('showSuccess', 'Service saved');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },

    setTouched(): void {
      this.touched = true;
    },

    fetchData(serviceId: string): void {
      api
        .get(`/services/${serviceId}/service-clients`)
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

    doAddServiceClient(selected: any[]): void {
      this.addServiceClientDialogVisible = false;

      api
        .post(`/services/${this.serviceId}/service-clients`, {
          items: selected,
        })
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
      const items: any[] = this.serviceClients.map((sc: ServiceClient) => ({
        id: sc.id,
        service_client_type: sc.service_client_type,
      }));

      this.removeServiceClients(items);
      this.confirmAllServiceClients = false;
    },
    removeServiceClient(member: any): void {
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
        .post(`/services/${this.serviceId}/service-clients/delete`, {
          items: serviceClients,
        })
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
  },
  watch: {
    isHttps(val) {
      // If user edits http to https --> change "ssl auth" to true
      if (val === true) {
        this.service.ssl_auth = true;
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
