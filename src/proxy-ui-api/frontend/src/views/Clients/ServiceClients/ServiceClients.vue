<template>
  <div>
    <div class="table-toolbar">
      <v-text-field
        v-model="search"
        :label="$t('serviceClients.searchPlaceHolder')"
        single-line
        hide-details
        data-test="search-service-client"
        class="search-input"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
      <v-btn
        color="primary"
        @click="addServiceClient"
        outlined
        rounded
        data-test="add-service-client"
        class="ma-0 rounded-button elevation-0"
        >{{ $t('serviceClients.addServiceClient') }}
      </v-btn>
    </div>

    <table class="xrd-table xrd-table-highlightable service-clients-table">
      <thead>
        <tr>
          <th>{{ $t('serviceClients.name') }}</th>
          <th>{{ $t('serviceClients.id') }}</th>
        </tr>
      </thead>
      <template v-if="serviceClients.length > 0">
        <tbody>
          <tr
            v-for="sc in this.filteredServiceClients()"
            v-bind:key="sc.id"
            @click="showAccessRights(sc.id)"
            data-test="open-access-rights"
          >
            <td>{{ sc.name }}</td>
            <td>{{ sc.id }}</td>
          </tr>
        </tbody>
      </template>
    </table>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import * as api from '@/util/api';
import { ServiceClient } from '@/openapi-types';

export default Vue.extend({
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
      api
        .get(`/clients/${this.id}/service-clients`, {})
        .then((response: any): void => (this.serviceClients = response.data))
        .catch((error: any) => this.$store.dispatch('showError', error));
    },
    addServiceClient(): void {
      this.$router.push(`/subsystem/serviceclients/${this.id}/add`);
    },
    filteredServiceClients(): ServiceClient[] {
      return this.serviceClients.filter((sc: ServiceClient) => {
        const searchWordLowerCase = this.search.toLowerCase();
        return (
          sc.name?.toLowerCase().includes(searchWordLowerCase) ||
          sc.id.toLowerCase().includes(searchWordLowerCase)
        );
      });
    },
    showAccessRights(serviceClientId: string) {
      this.$router.push(
        `/subsystem/${this.id}/serviceclients/${serviceClientId}`,
      );
    },
  },
  created() {
    this.fetchServiceClients();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
@import '../../../assets/colors';

.search-input {
  max-width: 300px;
}

.service-clients-table {
  margin-top: 40px;
}
</style>
