<template>
  <div>
    <toolbar/>
    <v-content class="pa-2 ma-2">
      <h1>Datatable demo</h1>
      Is authenticated: {{isAuthenticated}}
      <v-btn color="primary" @click="fetchCities">Always active FETCH DATA</v-btn>
      <p>{{ $t("message") }}</p>
      <v-select v-bind:items="locales" v-model="$i18n.locale" label="Select locale"></v-select>
    </v-content>
    <v-content>
      <dataTable/>
    </v-content>
  </div>
</template>

<script lang="ts">
import Vue from "vue";
import { mapGetters } from "vuex";
import DataTable from "../components/DataTable.vue";
import Toolbar from "../components/Toolbar.vue";

export default Vue.extend({
  components: {
    DataTable,
    Toolbar
  },
  data() {
    return {
      locales: ["en", "es", "ja"]
    };
  },
  methods: {
    fetchCities() {
      this.$store.dispatch("fetchData").then(
        response => {
          this.$bus.$emit('show-success', 'Great success!');
        },
        error => {
          this.$bus.$emit("show-error", error.message);
        }
      );
    }
  },
  computed: {
    ...mapGetters(["isAuthenticated"])
  }
});
</script>