<template>
  <v-layout align-center justify-center column>
    <v-layout align-center justify-center row elevation-0 buttons>
      <v-btn outline round color="primary" @click="fetchClients">FETCH DATA</v-btn>
      <v-btn outline round color="primary" @click="clearClients">CLEAR DATA</v-btn>
    </v-layout>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

export default Vue.extend({
  data: () => ({}),

  methods: {
    fetchClients() {
      this.$store.dispatch('fetchClients').then(
        (response) => {
          this.$bus.$emit('show-success', 'Clients fetched');
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
    clearClients(): void {
      this.$store.dispatch('clearData');
    },
  },
  computed: {
    ...mapGetters(['isAuthenticated']),
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
