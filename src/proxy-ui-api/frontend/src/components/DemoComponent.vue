<template>
  <v-layout class="demo-wrap" align-center justify-center column>
    <v-flex mb-4 class="caption">
      <p>Is authenticated: {{isAuthenticated}}</p>
      <p>Cookies: {{cookies}}</p>
    </v-flex>
    <v-flex mb-4>
      <p>{{ $t("message") }}</p>
      <v-select v-bind:items="locales" v-model="$i18n.locale" label="Select locale"></v-select>
    </v-flex>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

export default Vue.extend({
  data() {
    return {
      locales: ['en', 'es', 'ja'],
    };
  },
  computed: {
    ...mapGetters(['isAuthenticated']),
    cookies() {
      return document.cookie;
    },
  },
});
</script>

<style lang="scss" scoped>
.demo-wrap {
  border: 2px dashed gray;
  padding: 10px;
  margin-top: 30px;
  max-width: 700px;
}
</style>

