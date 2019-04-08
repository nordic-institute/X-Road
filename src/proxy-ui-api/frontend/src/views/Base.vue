<template>
  <div>
    <toolbar/>
    <v-layout class="main-content" align-center justify-center column>
      <v-tabs v-model="tab" class="main-tabs" color="white" grow mb-10>
        <v-tabs-slider color="secondary" class="xr-main-tabs-slider"></v-tabs-slider>

        <template v-for="tab in allowedTabs">
          <v-tab v-bind:key="tab.key" :to="tab.to">{{tab.name}}</v-tab>
        </template>
      </v-tabs>
      <v-layout mt-5 class="full-width">
        <transition name="fade" mode="out-in">
          <router-view/>
        </transition>
      </v-layout>
    </v-layout>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

import TabContent from '../components/TabContent.vue';
import Toolbar from '../components/Toolbar.vue';

export default Vue.extend({
  components: {
    TabContent,
    Toolbar,
  },
  data() {
    return {
      tab: null,
    };
  },
  computed: {
    ...mapGetters(['allowedTabs']),
  },
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
});
</script>

<style lang="scss">
.v-tabs__slider.xr-main-tabs-slider {
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

.full-width {
  width: 100%;
  max-width: 1280px;
}

.fade-enter-active,
.fade-leave-active {
  transition-duration: 0.2s;
  transition-property: opacity;
  transition-timing-function: ease;
}

.fade-enter,
.fade-leave-active {
  opacity: 0;
}
</style>
