<template>
  <div class="xrd-tab-max-width">
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
          data-test="remove-all"
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
              @click="remove(sc)">{{$t('action.remove')}}
            </v-btn>
          </td>
        </tr>
      </template>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts">
  import Vue from "vue";
  import * as api from '@/util/api';
  import SubViewTitle from '@/components/ui/SubViewTitle.vue';
  import {Endpoint, ServiceClient} from "@/types";
  import LargeButton from "@/components/ui/LargeButton.vue";

  export default Vue.extend({
    name: "EndpointAccessRights",
    components: {
      SubViewTitle,
      LargeButton
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
        serviceClients: [] as ServiceClient[]
      }
    },
    methods: {
      close(): void {
        this.$router.go(-1);
      },
      removeAll(): void {
        // NOOP
      },
      addSubjects(): void {
        // NOOP
      },
      remove(serviceClient: ServiceClient): void {
        // NOOP
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
    },
    created(): void {
      this.fetchData();
    }
  });

</script>

<style lang="scss" scoped>
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
