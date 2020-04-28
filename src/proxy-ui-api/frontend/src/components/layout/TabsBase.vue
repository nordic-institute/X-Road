<template>
  <v-layout class="main-content" align-center justify-center column>
    <v-tabs v-model="tab" class="main-tabs" grow mb-10 color="secondary" slider-size="4">
      <v-tabs-slider color="secondary" class="xrd-main-tabs-slider"></v-tabs-slider>
      <v-tab v-for="tab in allowedTabs" v-bind:key="tab.key" :to="tab.to" exact>{{ $t(tab.name) }}</v-tab>
    </v-tabs>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import { Tab } from '@/ui-types';
import { mainTabs } from '@/global';

export default Vue.extend({
  data() {
    return {
      tab: undefined as undefined | Tab,
    };
  },
  computed: {
    allowedTabs(): Tab[] {
      return this.$store.getters.getAllowedTabs(mainTabs);
    },
  },
});
</script>

<style lang="scss">
.v-tabs-slider.xrd-main-tabs-slider {
  width: 40px;
  margin-left: auto;
  margin-right: auto;
}
</style>

<style lang="scss" scoped>
.main-content {
  margin-top: 50px;
}

.main-tabs {
  width: 100%;
  max-width: 1000px;
}
</style>
