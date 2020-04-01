<template>
  <div>
    <div class="table-toolbar">
      <v-text-field v-model="search" label="Search" single-line hide-details class="search-input">
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
      <v-btn
        color="primary"
        @click="addSubject"
        outlined
        rounded
        class="ma-0 rounded-button elevation-0"
      >{{$t('serviceClients.addSubject')}}
      </v-btn>
    </div>

    <v-card flat>
      <table class="xrd-table service-clients-table">
        <tr>
          <th>{{$t('serviceClients.memberNameGroupDesc')}}</th>
          <th>{{$t('serviceClients.id')}}</th>
        </tr>
        <template v-if="serviceClients.length > 0">
          <tr v-for="sc in this.serviceClients">
            <td>{{sc.subject.member_name_group_description}}</td>
            <td>{{sc.subject.id}}</td>
          </tr>
        </template>
      </table>
    </v-card>

  </div>
</template>

<script lang="ts">
  import Vue from 'vue';
  import {mapGetters} from 'vuex';
  import * as api from '@/util/api';
  import {ServiceClient} from "@/types";

  export default Vue.extend({
    components: {},
    props: {
      id: {
        type: String,
        required: true,
      },
    },
    data() {
      return {
        serviceClients: [] as ServiceClient[],
        search: '' as String
      };
    },
    computed: {
      ...mapGetters(['client']),
    },
    methods: {
      fetchServiceClients(): void {
        api.get(`/clients/${this.id}/service-clients`)
          .then( ( serviceClients: any ): void => this.serviceClients = serviceClients.data )
          .catch( (error: any) =>
            this.$store.dispatch('showError', error));
      },
      addSubject(): void {
        // NOOP
      },
    },
    created() {
      this.fetchServiceClients();
      this.$store.dispatch('showError', 'serviceClients.serviceClientFetchFailure')
    },
  });
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';

.search-input {
  max-width: 300px;
}

.service-clients-table {
  margin-top: 40px;
}

</style>
