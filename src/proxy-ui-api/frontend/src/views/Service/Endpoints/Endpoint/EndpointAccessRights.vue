<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <div>
      <subViewTitle
        :title="`${endpoint.method}${endpoint.path}`"
        @close="close"
      />
    </div>

    <div class="group-members-row">
      <div class="row-title">{{ $t('accessRights.title') }}</div>
      <div class="row-buttons">
        <large-button
          @click="removeAll()"
          outlined
          data-test="remove-all-access-rights"
          >{{ $t('action.removeAll') }}
        </large-button>
        <large-button
          @click="toggleAddServiceClientsDialog()"
          outlined
          data-test="add-subjects-dialog"
          >{{ $t('accessRights.addServiceClients') }}
        </large-button>
      </div>
    </div>

    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{ $t('accessRights.memberName') }}</th>
          <th>{{ $t('accessRights.id') }}</th>
          <th>{{ $t('accessRights.rightsGiven') }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <template>
          <tr v-for="sc in serviceClients" :key="sc.id">
            <td>{{ sc.name }}</td>
            <td>{{ sc.id }}</td>
            <td>{{ sc.rights_given_at | formatDateTime }}</td>
            <td class="wrap-right-tight">
              <v-btn
                small
                outlined
                rounded
                color="primary"
                class="xrd-small-button xrd-table-button"
                @click="remove(sc)"
                data-test="remove-access-right"
                >{{ $t('action.remove') }}
              </v-btn>
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <!-- Confirm dialog remove Access Right subject -->
    <confirmDialog
      :dialog="confirmDelete"
      title="accessRights.removeTitle"
      text="accessRights.removeText"
      @cancel="resetDeletionSettings()"
      @accept="doRemoveSelectedServiceClients()"
    />

    <!-- Add access right subjects dialog -->
    <accessRightsDialog
      :dialog="addSubjectsDialogVisible"
      :existingServiceClients="serviceClients"
      :clientId="clientId"
      title="accessRights.addServiceClientsTitle"
      @cancel="toggleAddServiceClientsDialog"
      @serviceClientsAdded="doAddServiceClients"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import { Endpoint, ServiceClient } from '@/openapi-types';
import LargeButton from '@/components/ui/LargeButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import AccessRightsDialog from '@/views/Service/AccessRightsDialog.vue';

export default Vue.extend({
  name: 'EndpointAccessRights',
  components: {
    SubViewTitle,
    LargeButton,
    ConfirmDialog,
    AccessRightsDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
    clientId: {
      type: String,
      required: true,
    },
  },
  data: () => {
    return {
      endpoint: {} as Endpoint | {},
      serviceClients: [] as ServiceClient[],
      confirmDelete: false as boolean,
      serviceClientsToDelete: [] as ServiceClient[],
      addSubjectsDialogVisible: false as boolean,
      serviceClientsToAdd: [] as ServiceClient[],
    };
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },
    removeAll(): void {
      this.toggleConfirmDeleteDialog();
      this.serviceClientsToDelete = this.serviceClients;
    },
    remove(serviceClient: ServiceClient): void {
      this.toggleConfirmDeleteDialog();
      this.serviceClientsToDelete = [serviceClient];
    },
    resetDeletionSettings(): void {
      this.toggleConfirmDeleteDialog();
      this.serviceClientsToDelete = [];
    },
    toggleConfirmDeleteDialog(): void {
      this.confirmDelete = !this.confirmDelete;
    },
    toggleAddServiceClientsDialog(): void {
      this.addSubjectsDialogVisible = !this.addSubjectsDialogVisible;
    },
    fetchData(): void {
      api
        .get(`/endpoints/${this.id}`)
        .then((endpoint: any) => {
          this.endpoint = endpoint.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        });
      api
        .get(`/endpoints/${this.id}/service-clients`)
        .then((accessRights) => {
          this.serviceClients = accessRights.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        });
    },
    doRemoveSelectedServiceClients(): void {
      api
        .post(`/endpoints/${this.id}/service-clients/delete`, {
          items: this.serviceClientsToDelete,
        })
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            'accessRights.removeSubjectsSuccess',
          );
          this.fetchData();
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        })
        .finally(() => {
          this.toggleConfirmDeleteDialog();
          this.serviceClientsToDelete = [];
        });
    },
    doAddServiceClients(serviceClients: ServiceClient[]): void {
      api
        .post(`/endpoints/${this.id}/service-clients`, {
          items: serviceClients,
        })
        .then((accessRights) => {
          this.$store.dispatch(
            'showSuccess',
            'accessRights.addSubjectsSuccess',
          );
          this.serviceClients = accessRights.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error.message);
        })
        .finally(() => {
          this.toggleAddServiceClientsDialog();
        });
    },
  },
  created(): void {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import 'src/assets/colors';
@import 'src/assets/tables';
@import 'src/assets/global-style';

.group-members-row {
  width: 100%;
  display: flex;
  margin-top: 70px;
  align-items: baseline;
}

.row-buttons {
  display: flex;
  * {
    margin-left: 20px;
  }
}

.wrap-right-tight {
  display: flex;
  width: 100%;
  justify-content: flex-end;
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
</style>
