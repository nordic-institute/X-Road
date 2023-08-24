<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <v-app class="xrd-app">
    <!-- Dont show toolbar or footer in login view -->
    <app-toolbar v-if="loginView" />
    <v-main app>
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </v-main>
    <snack-bar />
    <app-footer v-if="loginView" />
  </v-app>
</template>

<script lang="ts">
// The root component of the Vue app
import { defineComponent } from 'vue';
import SnackBar from '@/components/ui/SnackBar.vue';
import AppFooter from '@/components/layout/AppFooter.vue';
import AppToolbar from '@/components/layout/AppToolbar.vue';
import { RouteName } from '@/global';

export default defineComponent({
  name: 'App',

  components: {
    AppFooter,
    AppToolbar,
    SnackBar,
  },
  computed: {
    loginView(): boolean {
      return this.$route.name !== RouteName.Login;
    },
  },
});
</script>

<style lang="scss">
@import '@/assets/global-style';
</style>

<style lang="scss" scoped>
@import '@/assets/colors';

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

// Set the app background color
.v-theme--light.v-application.xrd-app {
  background: $XRoad-WarmGrey30;
}
</style>
