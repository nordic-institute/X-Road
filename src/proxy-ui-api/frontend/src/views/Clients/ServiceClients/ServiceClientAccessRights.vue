<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <subViewTitle :title="serviceClientId" @close="close" />

    <v-card flat>
      <table class="xrd-table service-client-margin">
        <thead>
        <tr>
          <th>{{$t('serviceClients.name')}}</th>
          <th>{{$t('serviceClients.id')}}</th>
        </tr>
        </thead>
        <tr>
          <td>{{serviceClient.name}}</td>
          <td>{{serviceClient.id}}</td>
        </tr>
      </table>
    </v-card>

    <div class="group-members-row">
      <div class="row-title">{{$t('serviceClients.accessRights')}}</div>
      <div class="row-buttons">
        <large-button
          @click="removeAll()"
          outlined
          data-test="remove-all-access-rights"
          v-if="accessRights.length > 0"
        >{{$t('serviceClients.removeAll')}}
        </large-button>
        <large-button
          @click="showAddServiceDialog()"
          outlined
          data-test="add-subjects-dialog"
        >{{$t('serviceClients.addService')}}
        </large-button>
      </div>
    </div>

    <table class="xrd-table service-client-margin" v-if="accessRights.length > 0">
      <thead>
        <tr>
          <th>{{$t('serviceClients.serviceCode')}}</th>
          <th>{{$t('serviceClients.title')}}</th>
          <th>{{$t('serviceClients.accessRightsGiven')}}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(accessRight, index) in accessRights" v-bind:index="index" >
          <td>{{accessRight.service_code}}</td>
          <td>{{accessRight.service_title}}</td>
          <td>{{accessRight.rights_given_at}}</td>
          <td class="button-wrap"><v-btn
            small
            outlined
            rounded
            color="primary"
            class="xrd-small-button xrd-table-button"
            data-test="access-right-remove"
            @click="remove(accessRight)"
          >{{$t('action.remove')}}</v-btn></td>
        </tr>
      </tbody>
    </table>

    <h3 v-else class="service-client-margin">{{$t('serviceClients.noAccessRights')}}</h3>

    <div class="footer-buttons-wrap">
      <large-button @click="close()" data-test="close">{{$t('action.close')}}</large-button>
    </div>

    <AddServiceClientServiceDialog
      v-if="isAddServiceDialogVisible"
      :dialog="isAddServiceDialogVisible"
      :serviceCandidates="serviceCandidates()"
      @save="addService"
      @cancel="hideAddService">
    </AddServiceClientServiceDialog>

  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import {AccessRight, AccessRights, Service, ServiceClient, ServiceDescription} from '@/types';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import AddServiceClientServiceDialog from '@/views/Clients/ServiceClients/AddServiceClientServiceDialog.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
    LargeButton,
    AddServiceClientServiceDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    serviceClientId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      accessRights: [] as AccessRight[],
      serviceClient: {} as ServiceClient,
      isAddServiceDialogVisible: false as boolean,
      serviceDescriptions: [] as ServiceDescription[],
      services: [] as Service[],
    };
  },
  methods: {
    fetchData() {

      this.fetchAccessRights();

      api
        .get(`/clients/${this.id}/service-clients/${this.serviceClientId}`)
        .then( (response: any) => this.serviceClient = response.data)
        .catch( (error: any) => this.$store.dispatch('showError', error));

      api
        .get(`/clients/${this.id}/service-descriptions`)
        .then( (response: any) => {
          this.serviceDescriptions = response.data;
        })
        .catch( (error: any) => this.$store.dispatch('showError', error));
    },
    fetchAccessRights() {
      api
        .get(`/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights`)
        .then( (response: any) => this.accessRights = response.data)
        .catch( (error: any) => this.$store.dispatch('showError', error));
    },
    close(): void {
      this.$router.go(-1);
    },
    addService(accessRights: AccessRight[]): void {
      this.hideAddService();

      const accessRightsObject: AccessRights = { items: accessRights };
      api
        .post(`/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights`, accessRightsObject)
        .then( (response: any) => {
          this.$store.dispatch('showSuccess', 'serviceClients.addServiceClientAccessRightSuccess');
          this.fetchAccessRights();
        })
        .catch( (error: any) => this.$store.dispatch('showError', error));
    },
    hideAddService(): void {
      this.isAddServiceDialogVisible = false;
    },
    remove(): void {
      // NOOP
    },
    showAddServiceDialog(): void {
      this.isAddServiceDialogVisible = true;
    },
    removeAll(): void {
      // NOOP
    },
    serviceCandidates(): AccessRight[] {
      // returns whether given access right is for given service
      const isNotAccessRightToService = (service: Service, accessRight: AccessRight) =>
        accessRight.service_code !== service.service_code;

      // returns whether accessrights list contains any access that is for given service
      const noAccessRightsToService = (service: Service) =>
        this.accessRights.every( (accessRight: AccessRight) => isNotAccessRightToService(service, accessRight));

      // return services that can be added to this service client
      return this.serviceDescriptions
        // pick all services from service descriptions
        .reduce( (curr: Service[], next: ServiceDescription) => curr.concat(...next.services), this.services)
        // filter out services where this service client has access right already
        .filter( (service: Service) => noAccessRightsToService(service))
        // map to service candidates
        .map( (service: Service) => ({
          service_code: service.service_code,
          service_title: service.title,
        }));
    },
  },
  created() {
    this.fetchData();
  },

});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
@import '../../../assets/global-style';

.group-members-row {
  width: 100%;
  display: flex;
  margin-top: 70px;
  align-items: baseline;

  .row-buttons {
    display: flex;
    * {
      margin-left: 20px;
    }
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

}

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.service-client-margin {
  margin-top: 40px;
}

.footer-buttons-wrap {
  margin-top: 48px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid $XRoad-Grey40;
  padding-top: 20px;
}

</style>
