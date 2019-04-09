<template>
  <div class="xr-tab-max-width">
    <v-flex mb-4>
      <h1 v-if="client" class="display-1 mb-3">{{client.member_name}} ({{ $t("client.owner") }})</h1>
    </v-flex>
    <v-tabs slot="extension" v-model="tab" class="xr-tabs" color="white" grow>
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab key="details" :to="detailsRoute">{{ $t("tab.client.details") }}</v-tab>
      <v-tab key="internalServers" :to="serversRoute">{{ $t("tab.client.internalServers") }}</v-tab>
    </v-tabs>

    <router-view/>
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
    detailsRoute(): object {
      return {
        name: RouteName.MemberDetails,
        params: { id: this.id },
      };
    },
    serversRoute(): object {
      return {
        name: RouteName.MemberServers,
        params: { id: this.id },
      };
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

<style lang="scss" >
@import '../assets/tables';

.xr-tabs {
  border-bottom: #9b9b9b solid 1px;
}
</style>

