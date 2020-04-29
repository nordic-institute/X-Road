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
          @click="showConfirmDeleteAll = true"
          outlined
          data-test="remove-all-access-rights"
          v-if="accessRights.length > 0"
        >{{$t('serviceClients.removeAll')}}
        </large-button>
        <large-button
          @click="addService()"
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

    <!-- Confirm dialog delete group -->
    <confirmDialog
      :dialog="showConfirmDeleteAll"
      title="serviceClients.removeAllTitle"
      text="serviceClients.removeAllText"
      @cancel="showConfirmDeleteAll = false"
      @accept="removeAll()"
    />

  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import {AccessRight, ServiceClient} from '@/types';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';

export default Vue.extend({
  components: {
    SubViewTitle,
    LargeButton,
    ConfirmDialog,
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
      showConfirmDeleteAll: false as boolean,
    };
  },
  methods: {
    fetchData() {

      api
        .get(`/clients/${this.id}/service-clients/${this.serviceClientId}`)
        .then( (response: any) => this.serviceClient = response.data)
        .catch( (error: any) =>
          this.$store.dispatch('showError', error));

      this.fetchAccessRights();

    },
    fetchAccessRights(): void {
      api
        .get(`/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights`)
        .then( (response: any) => this.accessRights = response.data)
        .catch( (error: any) =>
          this.$store.dispatch('showError', error));
    },
    close() {
      this.$router.go(-1);
    },
    remove(accessRight: AccessRight) {
      api
        .post(`/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights/delete`,
          { items: [{ service_code: accessRight.service_code }]})
        .then( () => {
          this.$store.dispatch('showSuccess', 'serviceClients.removeSuccess');
          if (this.accessRights.length === 1) {
            this.accessRights = [];
          } else {
            this.fetchAccessRights();
          }
        })
        .catch( (error: any) => this.$store.dispatch('showError', error));
    },
    addService() {
      // NOOP
    },
    removeAll() {
      this.showConfirmDeleteAll = false;

      api
        .post(`/clients/${this.id}/service-clients/${this.serviceClientId}/access-rights/delete`,
          { items: this.accessRights.map( (item: AccessRight) => ({service_code: item.service_code}))})
        .then( () => {
          this.$store.dispatch('showSuccess', 'serviceClients.removeSuccess');
          this.accessRights = [];
        })
        .catch( (error: any) => this.$store.dispatch('showError', error));
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
