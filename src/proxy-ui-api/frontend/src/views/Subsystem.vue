<template>
  <div class="xr-tab-max-width">
    <v-flex mb-4>
      <h1 v-if="client" class="display-1 mb-3">{{client.subsystem_code}} (subsystem)</h1>
    </v-flex>
    <v-tabs slot="extension" v-model="tab" class="xr-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to">{{tab.name}}</v-tab>
    </v-tabs>

    <router-view/>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

import { mapGetters } from 'vuex';
/*
import ClientDetails from '@/components/ClientDetails.vue';
import InternalServers from '@/components/InternalServers.vue';
import LocalGroups from '@/components/LocalGroups.vue';
*/
import { Permissions, RouteName } from '@/global';

export default Vue.extend({
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      tab: null,
    };
  },
  computed: {
    ...mapGetters(['client']),
    tabs(): any {
      return [
        {
          key: 'details',
          name: 'Details',
          to: {
            name: RouteName.SubsystemDetails,
            params: { id: this.id },
          },
        },
        {
          key: 'serviceClients',
          name: 'Service Clients',
          to: {
            name: RouteName.SubsystemDetails,
            params: { id: this.id },
          },
        },
        {
          key: 'services',
          name: 'Services',
          to: {
            name: RouteName.SubsystemDetails,
            params: { id: this.id },
          },
        },
        {
          key: 'internalServers',
          name: 'Internal Servers',
          to: {
            name: RouteName.SubsystemServers,
            params: { id: this.id },
          },
        },
        {
          key: 'localGroups',
          name: 'Local Groups',
          to: {
            name: RouteName.SubsystemLocalGroups,
            params: { id: this.id },
          },
        },
      ];
    },
  },
  created() {
    this.fetchClient(this.id);
  },
  methods: {
    fetchClient(id: string) {
      this.$store.dispatch('fetchClient', id).catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },
  },
});
</script>


