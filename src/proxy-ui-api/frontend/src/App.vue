<template>
  <v-app>
    <div>
      <transition name="fade" mode="out-in">
        <router-view/>
      </transition>
    </div>
    <snackbar ref="snackbar"></snackbar>
  </v-app>
</template>

<script>
import SnackbarMixin from "./components/SnackbarMixin";

export default {
  name: "App",
  mixins: [SnackbarMixin],
};
</script>

<style lang="scss" scoped>
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

