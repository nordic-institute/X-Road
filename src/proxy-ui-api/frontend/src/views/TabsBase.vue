<template>
  <v-layout class="main-content" align-center justify-center column>
    <v-tabs v-model="tab" class="main-tabs" color="white" grow mb-10>
      <v-tabs-slider color="secondary" class="xrd-main-tabs-slider"></v-tabs-slider>

      <template v-for="tab in allowedTabs">
        <v-tab v-bind:key="tab.key" :to="tab.to">{{ $t(tab.name) }}</v-tab>
      </template>
    </v-tabs>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

export default Vue.extend({
  data() {
    return {
      tab: null,
    };
  },
  computed: {
    ...mapGetters(['allowedTabs']),
  },
});
</script>

<style lang="scss">
.v-tabs__slider.xrd-main-tabs-slider {
  height: 4px;
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
