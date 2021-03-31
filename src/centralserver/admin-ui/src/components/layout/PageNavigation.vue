<template>
  <div>
    <v-tabs :background-color="'transparent'" class="page-navigation-row">
      <v-tab v-for="item in items" v-bind:key="item.url" :to="item.url">{{
        item.label
      }}</v-tab>
    </v-tabs>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Prop } from 'vue/types/options';

export interface NavigationItem {
  url: string;
  label: string;
}

export default Vue.extend({
  name: 'PageNavigation',
  props: {
    items: {
      type: Array as Prop<NavigationItem[]>,
      required: true,
    },
  },
});
</script>

<style scoped lang="scss">
@import '../../assets/colors';

.page-navigation-row {
  // Unset default purple background
  .v-tab::before:hover {
    opacity: 0 !important;
  }
}
</style>
