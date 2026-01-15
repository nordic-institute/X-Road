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
  <v-navigation-drawer class="xrd-rail-nav px-1" width="96" permanent>
    <v-list-item class="xrd xrd-rail-item-logo mx-1 opacity-100" density="compact" variant="plain" to="/" rounded>
      <v-img class="ma-auto mb-3" width="48px" :src="logo" />
    </v-list-item>

    <slot />
    <template #append>
      <div class="w-100 text-center">
        <XrdThemeSwitcher class="xrd mx-auto my-1" />
      </div>
    </template>
  </v-navigation-drawer>
  <slot name="sub-nav" />
</template>

<script lang="ts" setup>
import _logoLight from '../assets/Logo-vertical-dark.svg';
import _logoDark from '../assets/Logo-vertical-light.svg';
import { computed } from 'vue';
import { useThemeHelper } from '../composables';
import { XrdThemeSwitcher } from '../components';

const { isDark } = useThemeHelper();

const logo = computed(() => (isDark.value ? _logoDark : _logoLight));
</script>
<style lang="scss" scoped>
.xrd-rail-nav {
  padding: 24px 8px;
  border-right-width: 0;

  //eslint-disable-next-line vue-scoped-css/no-unused-selector
  .v-list-item {
    margin: 0 8px;
    padding: 8px 0 12px;

    &.xrd-rail-item-logo {
      padding: 0;
    }
  }
}
</style>
