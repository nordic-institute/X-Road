<template>
  <v-layout align-center justify-center column>
    <clientsDataTable/>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import ClientsDataTable from '../components/ClientsDataTable.vue';
export default Vue.extend({
  components: {
    ClientsDataTable,
  },
  data: () => ({}),

  methods: {
    fetchClients() {
      this.$store.dispatch('fetchClients').then(
        (response) => {
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
  },
  created() {
    this.fetchClients();
  },
});
</script>

<style lang="scss" scoped>
.buttons {
  width: 100%;
  max-width: 1280px;
}
</style>
