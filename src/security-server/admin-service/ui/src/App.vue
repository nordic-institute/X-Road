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
<!-- This is the root component of the Vue app -->
<template>
  <v-app class="xrd-app">
    <!-- Dont show toolbar or footer in login view -->
    <AppToolbar v-if="loginView" />
    <v-main app>
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </v-main>
    <XrdSnackBar
      :success-notifications="notificationStore.successNotifications"
      @close="notificationStore.deleteSuccessNotification($event.timeAdded)"
    />
    <XrdAppFooter v-if="loginView" />
  </v-app>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import axios from 'axios';
import { XrdAppFooter, XrdSnackBar } from '@niis/shared-ui';
import AppToolbar from '@/components/layout/AppToolbar.vue';
import { RouteName } from '@/global';
import { useUser } from '@/store/modules/user';
import { useRoute, useRouter } from 'vue-router';
import { useNotifications } from '@/store/modules/notifications';

const route = useRoute();
const router = useRouter();
const userStore = useUser();
const notificationStore = useNotifications();

const loginView = computed(() => {
  return route.name !== RouteName.Login;
});

// Add a response interceptor
axios.interceptors.response.use(
  (response) => {
    // Set user authentication status in store
    userStore.authUser();
    return response;
  },
  (error) => {
    /*
      Check if error is a proper "unauthorized error" meaning it is not happening in sending login form data.
      Also, the response from session timeout polling is handled in AppBase -component
     */
    if (
      error?.response?.status === 401 &&
      router.currentRoute.value.name !== 'login'
    ) {
      // if you ever get an unauthorized, logout the user
      userStore.setSessionAlive(false);
    }
    // If the request is made with responseType: blob, but backend responds with json error
    if (
      error.request.responseType === 'blob' &&
      error.response.data instanceof Blob &&
      error.response.data.type &&
      error.response.data.type.toLowerCase().indexOf('json') != -1
    ) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();

        reader.onload = () => {
          error.response.data = JSON.parse(reader.result as string);
          resolve(Promise.reject(error));
        };

        reader.onerror = () => {
          reject(error);
        };

        reader.readAsText(error.response.data);
      });
    }

    // Do something with response error
    return Promise.reject(error);
  },
);

// Session-status api is called before accessing any view. The session-status data is only used to prevent
// opening views that user aren't allowed to see (flickering).
userStore.fetchSessionStatus();
</script>

<!-- eslint-disable-next-line  vue-scoped-css/enforce-style-type -->
<style lang="scss">
@use '@niis/shared-ui/src/assets/global-style';
</style>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/colors';

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

/* Set the app background color */
.v-theme--light.v-application.xrd-app {
  background: colors.$WarmGrey30;
}
</style>
