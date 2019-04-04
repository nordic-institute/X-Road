<template>
  <div>
    <toolbar/>
    <router-view name="top"/>

    <v-layout mt-5 class="full-width">
      <transition name="fade" mode="out-in">
        <router-view/>
      </transition>
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
.full-width {
  width: 100%;
  max-width: 1280px;
  display: flex;
  justify-content: center;
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
