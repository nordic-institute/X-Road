<template>
  <v-toolbar dark color="primary">
    <v-toolbar-side-icon></v-toolbar-side-icon>

    <v-toolbar-title class="white--text">Title</v-toolbar-title>

    <v-spacer></v-spacer>

    <!--
    <v-btn icon>
      <v-icon>apps</v-icon>
    </v-btn>

    <v-btn icon @click="refresh">
      <v-icon>refresh</v-icon>
    </v-btn>
    -->
    Username?

    <v-menu bottom left>
      <v-btn slot="activator" dark icon>        
        <v-icon>person_outline</v-icon>
      </v-btn>

      <v-list>
        <v-list-tile v-for="(item, i) in items" :key="i" @click="logout">
          <v-list-tile-title>{{ item.title }}</v-list-tile-title>
        </v-list-tile>
      </v-list>
    </v-menu>
  </v-toolbar>
</template>

<script lang="ts">
import Vue from "vue";

export default Vue.extend({
  name: "toolbar",
  data: () => ({
    items: [
      { title: "Logout" },
      { title: "Click Me" },
      { title: "Click Me 2" }
    ]
  }),
  methods: {
    refresh(): void {
      console.log("Refresh!");
    },
    logout(): void {
      console.log("logout");
      this.$store.dispatch("logout");
    },
  }
});
</script>