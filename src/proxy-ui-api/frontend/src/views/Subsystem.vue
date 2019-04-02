<template>
  <div class="xr-tab-max-width">
    <v-flex mb-4>
      <h1 v-if="client" class="display-1 mb-3">{{client.subsystem_code}} (subsystem)</h1>
    </v-flex>
    <v-tabs slot="extension" v-model="tab" class="xr-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab key="details">Details</v-tab>
      <v-tab key="serviceClients">Service Clients</v-tab>
      <v-tab key="services">Services</v-tab>
      <v-tab key="internalServers">Internal Servers</v-tab>
      <v-tab key="localGroups">Local Groups</v-tab>
    </v-tabs>

    <v-tabs-items v-model="tab">
      <v-tab-item key="details">
        <clientDetails :id="$route.query.id"/>
      </v-tab-item>
      <v-tab-item key="serviceClients">
        <v-card flat>
          <v-card
            class="mt-5 text-xs-center"
            color="grey lighten-2"
            height="250px"
          >Service Clients placeholder</v-card>
        </v-card>
      </v-tab-item>
      <v-tab-item key="services">
        <v-card flat>
          <v-card-text>Services placeholder</v-card-text>
        </v-card>
      </v-tab-item>
      <v-tab-item key="internalServers">
        <v-card flat>
          <v-card-text>Internal servers placeholder</v-card-text>
        </v-card>
      </v-tab-item>
      <v-tab-item key="localGroups">
        <v-card flat>
          <v-card-text>Local Groups placeholder</v-card-text>
        </v-card>
      </v-tab-item>
    </v-tabs-items>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { mapGetters } from 'vuex';
import ClientDetails from '@/components/ClientDetails.vue';

export default Vue.extend({
  computed: {
    ...mapGetters(['client']),
  },
  components: {
    ClientDetails,
  },
  data() {
    return {
      tab: null,
    };
  },
});
</script>


