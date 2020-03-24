<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <v-flex mb-4>
      <h1 v-if="client" class="display-1 mb-3">{{client.subsystem_code}} ({{ $t('subsystem') }})</h1>
    </v-flex>
    <v-tabs v-model="tab" class="xrd-tabs" color="secondary" grow slider-size="4">
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to">{{ $t(tab.name) }}</v-tab>
    </v-tabs>

    <router-view />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
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
    tabs(): any[] {
      const allTabs = [
        {
          key: 'details',
          name: 'tab.client.details',
          to: {
            name: RouteName.SubsystemDetails,
            params: { id: this.id },
          },
        },
        {
          key: 'serviceClients',
          name: 'tab.client.serviceClients',
          to: {
            name: RouteName.SubsystemServiceClients,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_ACL_SUBJECTS,
        },
        {
          key: 'services',
          name: 'tab.client.services',
          to: {
            name: RouteName.SubsystemServices,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_SERVICES,
        },
        {
          key: 'internalServers',
          name: 'tab.client.internalServers',
          to: {
            name: RouteName.SubsystemServers,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_INTERNAL_CERTS,
        },
        {
          key: 'localGroups',
          name: 'tab.client.localGroups',
          to: {
            name: RouteName.SubsystemLocalGroups,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_LOCAL_GROUPS,
        },
      ];

      return this.$store.getters.getAllowedTabs(allTabs);
    },
  },
  created() {
    this.fetchClient(this.id);
  },
  methods: {
    fetchClient(id: string): void {
      this.$store.dispatch('fetchClient', id).catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
  },
});
</script>
