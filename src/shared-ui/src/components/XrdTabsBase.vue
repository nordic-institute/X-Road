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
  <v-navigation-drawer class="xrd-rail-nav" width="96" permanent>
    <v-list-item class="xrd-rail-item-logo" density="compact">
      <v-img class="ma-auto mb-3" width="48px" :src="logo" />
    </v-list-item>

    <v-list-item
      v-for="tab in tabs"
      :key="tab.key"
      :to="tab.to"
      class="xrd-rail-item-nav body-small text-center font-weight-bold"
      variant="plain"
      density="compact"
    >
      <template #default="{ isActive }">
        <v-list-item-title class="text-center mb-1">
          <v-chip variant="flat" :color="isActive ? 'accent-container' : 'surface-variant'">
            <v-icon size="x-large" :icon="tab.icon" :filled="isActive" />
          </v-chip>
        </v-list-item-title>

        <div :class="[isActive ? 'text-accent' : 'text-primary']">
          {{ $t(tab.name) }}
        </div>
      </template>
    </v-list-item>

    <v-divider color="border-strong" class="ma-2"></v-divider>

    <v-list-item
      :active="userOptions"
      class="xrd-rail-item-options"
      variant="plain"
      density="compact"
      lines="one"
      @click="userOptions = !userOptions"
    >
      <v-list-item-title class="text-center mb-1">
        <v-chip variant="flat" :color="userOptions ? 'accent-container' : 'surface-variant'">
          <v-icon size="x-large" icon="msr-account-box" />
        </v-chip>
      </v-list-item-title>
      <v-list-item-subtitle class="body-small text-center font-weight-bold" :class="[userOptions ? 'text-accent' : 'text-primary']">
        {{ userName }}
      </v-list-item-subtitle>
    </v-list-item>
  </v-navigation-drawer>

  <v-navigation-drawer v-model="userOptions" class="xrd-rail-options" width="176" temporary>
    <v-list-item class="xrd-rail-item-username" density="compact">
      <v-list-item-title class="body-small font-weight-bold text-secondary">
        {{ userName }}
      </v-list-item-title>
    </v-list-item>
    <v-list v-model:opened="expandedUserOptions" :selected="[currentLanguage]" density="compact" slim @update:selected="changeLanguage">
      <v-list-group>
        <template #activator="{ props }">
          <v-list-item
            prepend-icon="msr-language"
            v-bind="props"
            rounded="xl"
            class="xrd-rail-item-lang-select"
            base-color="primary"
            color="primary"
          >
            <template #prepend>
              <v-icon icon="msr-language"></v-icon>
            </template>
            <v-list-item-title class="body-small font-weight-bold">{{ displayNames.of(currentLanguage) }}</v-list-item-title>
          </v-list-item>
        </template>
        <v-list-item
          v-for="lang in supportedLanguages"
          :key="lang"
          :value="lang"
          rounded="xl"
          variant="flat"
          class="xrd-rail-item-lang mt-1 mb-1"
          base-color="surface-variant"
          color="accent-container"
        >
          <v-list-item-title class="body-small font-weight-bold">{{ displayNames.of(lang) }}</v-list-item-title>
        </v-list-item>
      </v-list-group>
      <v-list-item class="xrd-rail-item-logout" rounded="xl" base-color="primary" @click="logoutApp">
        <template #prepend>
          <v-icon icon="msr-logout"></v-icon>
        </template>
        <v-list-item-title class="body-small font-weight-bold">{{ $t('login.logOut') }}</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-navigation-drawer>
</template>

<script lang="ts" setup>
import { PropType, ref, inject } from 'vue';
import { Tab, key } from '../utils';
import { useLanguageHelper } from '../plugins/i18n';
import _logo from '../assets/xrd8/Logo-vertical-dark.png';

defineProps({
  tabs: {
    type: Object as PropType<Tab[]>,
    required: true,
  },
  userName: {
    type: String,
    required: true,
  },
});

const userOptions = ref(false);
const expandedUserOptions = ref([]);
const logo = _logo;

const { currentLanguage, supportedLanguages, selectLanguage, displayNames } = useLanguageHelper();
const user = inject(key.user);

const routing = inject(key.routing);

function logoutApp(): void {
  user?.logout();
  routing?.toLogin();
}

async function changeLanguage(langs: string[]) {
  await selectLanguage(langs[0]);
}
</script>
<style lang="scss" scoped>
.xrd-rail-nav {
  padding: 24px 0;
  border-right-width: 0;

  .v-list-item {
    margin: 0 8px;
    padding: 8px 0 12px;

    &.xrd-rail-item-logo {
      padding: 0;
    }
  }
}

.xrd-rail-options {
  padding: 16px 4px;
  border-left-width: 1px;

  .v-list-item {
    padding: 8px 12px;
  }
}

.v-list-item--variant-plain:not(:hover) {
  opacity: 0.8;
}

.xrd-rail-item-lang.v-list-item--active:not(:hover) {
  :deep(.v-list-item__overlay) {
    opacity: 0;
  }
}

.xv-list-item--link.v-list-item.v-list-item--active {
  opacity: 1;

  &:not(:hover),
  &:not(:focus-visible) {
    :deep(.v-list-item__overlay) {
      opacity: 0;
    }
  }
}
</style>
