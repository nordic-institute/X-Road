<template>
  <div>
    <toolbar/>
    <router-view name="top"/>
    <v-layout align-center justify-center>
      <v-layout mt-5 align-center justify-center class="base-full-width">
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

import Toolbar from '../components/Toolbar.vue';

export default Vue.extend({
  components: {
    Toolbar,
  },
});
</script>

<style lang="scss" scoped>

.base-full-width {
  width: 100%;
  max-width: 1280px;  
  margin: 10px;
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
