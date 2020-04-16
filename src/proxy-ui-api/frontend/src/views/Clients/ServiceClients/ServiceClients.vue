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
          <tr v-for="sc in this.filteredServiceClients()">
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
  import {ServiceClient} from '@/types';

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
        search: '' as string,
      };
    },
    computed: {
      ...mapGetters(['client']),
    },
    methods: {
      fetchServiceClients() {
        api.get(`/clients/${this.id}/service-clients`, {})
          .then( ( response: any ): void => this.serviceClients = response.data )
          .catch( (error: any) =>
            this.$store.dispatch('showError', error));
      },
      addSubject(): void {
        // NOOP
      },
      filteredServiceClients() {
        return this.serviceClients.filter( (sc: ServiceClient) => {
          const memberNameOrGroupDescription = sc.subject.member_name_group_description?.toLowerCase();
          const subjectId = sc.subject.id.toLowerCase();
          const searchWordLowerCase = this.search.toLowerCase();
          return memberNameOrGroupDescription?.includes(searchWordLowerCase) || subjectId.includes(searchWordLowerCase);
        });
      },
    },
    created() {
      this.fetchServiceClients();
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
