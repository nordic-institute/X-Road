<template>
  <div class="xrd-tab-max-width">
    <v-flex mb-4>
      <h1 v-if="client" class="display-1 mb-3">{{client.member_name}} ({{ $t("client.owner") }})</h1>
    </v-flex>
    <v-tabs slot="extension" v-model="tab" class="xrd-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to">{{ $t(tab.name) }}</v-tab>
    </v-tabs>

    <router-view />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import ClientDetails from '@/components/ClientDetails.vue';
import InternalServers from '@/components/InternalServers.vue';
import { Permissions, RouteName } from '@/global';

export default Vue.extend({
  components: {
    ClientDetails,
    InternalServers,
  },
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
    tabs(): object[] {
      const allTabs = [
        {
          key: 'details',
          name: 'tab.client.details',
          to: {
            name: RouteName.MemberDetails,
            params: { id: this.id },
          },
        },
        {
          key: 'internalServers',
          name: 'tab.client.internalServers',
          to: {
            name: RouteName.MemberServers,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_INTERNAL_CERTS,
        },
      ];

      return this.$store.getters.getAllowedTabs(allTabs);
    },
    localGroupsRoute(): object {
      return {
        name: RouteName.SubsystemLocalGroups,
        params: { id: this.id },
      };
    },
  },
  created() {
    this.fetchClient(this.id);
  },
  methods: {
    fetchClient(id: string): void {
      this.$store.dispatch('fetchClient', id).catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },
  },
});
</script>
