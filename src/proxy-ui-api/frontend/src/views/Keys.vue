<template>
  <v-layout align-center justify-center column>
    <v-layout align-center justify-center row elevation-0 buttons>
      <v-btn outline round color="primary" @click="fetchCities">FETCH DATA</v-btn>
      <v-btn outline round color="primary" @click="clearCities">CLEAR DATA</v-btn>
    </v-layout>

    <dataTable/>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import DataTable from '../components/DataTable.vue';
export default Vue.extend({
  components: {
    DataTable,
  },
  data: () => ({}),

  methods: {
    fetchCities() {
      this.$store.dispatch('fetchData').then(
        (response) => {
          this.$bus.$emit('show-success', 'Great success!');
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
    clearCities(): void {
      this.$store.dispatch('clearData');
    },
    demoLogout(): void {
      this.$store.dispatch('demoLogout');
    },
  },
  computed: {
    ...mapGetters(['isAuthenticated']),
    cookies() {
      return document.cookie.split(';');
    },
  },
  created() {
    this.fetchCities();
  },
});
</script>

<style lang="scss" scoped>
.buttons {
  width: 100%;
  max-width: 1280px;
}
</style>
