<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <div>
      <subViewTitle
        :title="`${endpoint.method}${endpoint.path}`"
        @close="close"
      />
    </div>

    <div class="group-members-row">
      <div class="row-title">{{$t('access.accessRights')}}</div>
      <div class="row-buttons">
        <large-button
          @click="removeAll()"
          outlined
          data-test="remove-all-access-rights"
        >{{$t('action.removeAll')}}
        </large-button>
        <large-button
          @click="addSubjects()"
          outlined
          data-test="add-subjects-dialog"
        >{{$t('access.addSubjects')}}
        </large-button>
      </div>
    </div>

    <table class="xrd-table">
      <thead>
      <tr>
        <th>{{$t('access.memberName')}}</th>
        <th>{{$t('access.id')}}</th>
        <th>{{$t('access.rightsGiven')}}</th>
        <th></th>
      </tr>
      </thead>
      <tbody>
      <template>
        <tr v-for="sc in serviceClients">
          <td>{{ sc.subject.member_name_group_description }}</td>
          <td>{{ sc.subject.id }}</td>
          <td>{{ sc.rights_given_at | formatDateTime }}</td>
          <td>
            <v-btn
              small
              outlined
              rounded
              color="primary"
              class="xrd-small-button xrd-table-button"
              @click="remove(sc)" data-test="remove-access-right">{{$t('action.remove')}}
            </v-btn>
          </td>
        </tr>
      </template>
      </tbody>
    </table>

    <!-- Confirm dialog remove Access Right subject -->
    <confirmDialog
      :dialog="confirmDelete"
      title="access.removeTitle"
      text="access.removeText"
      @cancel="resetDeletionSettings()"
      @accept="doRemoveSelectedSubjects()"
    />

  </div>
</template>

<script lang="ts">
  import Vue from "vue";
  import * as api from '@/util/api';
  import SubViewTitle from '@/components/ui/SubViewTitle.vue';
  import {Endpoint, ServiceClient, Subject} from "@/types";
  import LargeButton from "@/components/ui/LargeButton.vue";
  import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';

  export default Vue.extend({
    name: "EndpointAccessRights",
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
    },
    data: () => {
      return {
        endpoint: {} as Endpoint | {},
        serviceClients: [] as ServiceClient[],
        confirmDelete: false as boolean,
        subjectsToDelete: [] as Subject[]
      }
    },
    methods: {
      close(): void {
        this.$router.go(-1);
      },
      addSubjects(): void {
        // NOOP
      },
      removeAll(): void {
        this.confirmDelete = true;
        this.subjectsToDelete = this.serviceClients.map( (sc: ServiceClient) => sc.subject) as Subject[];
      },
      remove(serviceClient: ServiceClient): void {
        this.confirmDelete = true;
        this.subjectsToDelete = [serviceClient.subject];
      },
      resetDeletionSettings(): void {
        this.confirmDelete = false;
        this.subjectsToDelete = [];
      },
      fetchData(): void {
        api
          .get(`/endpoints/${this.id}`)
          .then((endpoint: any) => {
            this.endpoint = endpoint.data;
          })
          .catch((error) => {
            this.$bus.$emit('show-error', error.message);
          });
        api
          .get(`/endpoints/${this.id}/access-rights`)
          .then((accessRights: any) => {
            this.serviceClients = accessRights.data;
          })
          .catch((error) => {
            this.$bus.$emit('show-error', error.message);
          });
      },
      doRemoveSelectedSubjects(): void {
        api
          .post(`/endpoints/${this.id}/access-rights`, { items: this.subjectsToDelete })
          .then( () => {
            this.$bus.$emit('show-success', 'endpoints.editSuccess');
            this.fetchData();
          })
          .catch( (error) => {
            this.$bus.$emit('show-error', error.message);
          }).finally( () => {
            this.confirmDelete = false;
            this.subjectsToDelete = [];
          });
      },
    },
    created(): void {
      this.fetchData();
    }
  });

</script>

<style lang="scss" scoped>
  @import '../../assets/colors';
  @import '../../assets/tables';
  @import '../../assets/global-style';

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
