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
  <XrdMainNavigationContainer>
    <v-list-item
      v-for="tab in tabs"
      :key="tab.key"
      data-test="main-navigation-item"
      class="xrd pa-0 mt-2 mb-3 mx-1 xrd-rail-item-nav body-small text-center font-weight-bold"
      variant="plain"
      density="compact"
      tabindex="0"
      rounded
      :to="tab.to"
    >
      <template #default="{ isActive }">
        <v-list-item-title class="text-center mb-1">
          <v-chip variant="flat" :color="isActive ? 'accent-container' : ''">
            <v-icon size="x-large" :icon="tab.icon" :filled="isActive" />
          </v-chip>
        </v-list-item-title>

        <div data-test="main-navigation-item-name" :class="[isActive ? 'text-accent' : 'text-primary', 'font-weight-medium']">
          {{ $t(tab.name) }}
        </div>
      </template>
    </v-list-item>

    <v-divider color="border-strong opacity-20" class="ma-2"></v-divider>

    <v-list-item
      data-test="user-menu"
      class="xrd pa-0 mt-2 mb-3 mx-1 xrd-rail-item-options"
      variant="plain"
      density="compact"
      lines="one"
      :active="userOptions"
      @click="userOptions = !userOptions"
    >
      <v-list-item-title class="text-center mb-1">
        <v-chip variant="flat" :color="userOptions ? 'accent-container' : ''">
          <v-icon size="x-large" icon="account_box" />
        </v-chip>
      </v-list-item-title>
      <v-list-item-subtitle class="body-small text-center font-weight-bold" :class="[userOptions ? 'text-accent' : 'text-primary']">
        {{ userName }}
      </v-list-item-subtitle>
    </v-list-item>
    <template #sub-nav>
      <v-navigation-drawer v-model="userOptions" class="xrd-rail-options pr-1" temporary>
        <v-list-item class="xrd-rail-item-username" density="compact">
          <v-list-item-title class="body-small font-weight-bold text-secondary">
            {{ userName }}
          </v-list-item-title>
        </v-list-item>
        <v-list
          v-if="userOptions"
          v-model:opened="expandedUserOptions"
          :selected="[currentLanguage]"
          density="compact"
          slim
          @update:selected="changeLanguage"
        >
          <v-list-group>
            <template #activator="{ props }">
              <v-list-item
                prepend-icon="language"
                v-bind="props"
                rounded="pill"
                class="xrd xrd-rail-item-lang-select mx-1"
                base-color="primary"
                color="primary"
                tabindex="0"
              >
                <template #prepend>
                  <v-icon icon="language" />
                </template>
                <v-list-item-title class="body-small font-weight-bold">{{ displayNames.of(currentLanguage) }}</v-list-item-title>
              </v-list-item>
            </template>
            <v-list-item
              v-for="lang in supportedLanguages"
              :key="lang"
              :value="lang"
              rounded="pill"
              variant="flat"
              class="xrd xrd-rail-item-lang mt-1 mb-1 mx-1"
              color="accent-container"
              tabindex="0"
            >
              <v-list-item-title class="body-small font-weight-bold">{{ displayNames.of(lang) }}</v-list-item-title>
            </v-list-item>
          </v-list-group>
          <v-list-item
            v-if="databaseBasedAuthentication"
            data-test="password-button"
            class="xrd xrd-rail-item-password mx-1"
            rounded="pill"
            base-color="primary"
            tabindex="0"
            @click="changePasswordDialog = true"
          >
            <template #prepend>
              <v-icon icon="lock_reset"></v-icon>
            </template>
            <v-list-item-title class="body-small font-weight-bold">{{ $t('login.changePassword') }}</v-list-item-title>
          </v-list-item>
          <v-list-item
            data-test="logout-button"
            class="xrd xrd-rail-item-logout mx-1"
            rounded="pill"
            base-color="primary"
            tabindex="0"
            @click="emit('logout')"
          >
            <template #prepend>
              <v-icon icon="logout"></v-icon>
            </template>
            <v-list-item-title class="body-small font-weight-bold">{{ $t('login.logOut') }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-navigation-drawer>
      <XrdAdminUserPasswordChangeDialog
        v-if="changePasswordDialog"
        :admin-users-handler="adminUsersHandler"
        :username="userName"
        @password-changed="
          changePasswordDialog = false;
          userOptions = false;
        "
        @cancel="changePasswordDialog = false"
      />
    </template>
  </XrdMainNavigationContainer>
</template>

<script lang="ts" setup>
import { PropType, ref } from 'vue';
import { useLanguageHelper } from '../plugins/i18n';
import XrdMainNavigationContainer from './XrdMainNavigationContainer.vue';
import { AdminUsersHandler, Tab } from '../types';
import { XrdAdminUserPasswordChangeDialog } from '../components/admin-users';

defineProps({
  tabs: {
    type: Object as PropType<Tab[]>,
    required: true,
  },
  userName: {
    type: String,
    required: true,
  },
  hideNavigation: {
    type: Boolean,
    default: false,
  },
  databaseBasedAuthentication: {
    type: Boolean,
    default: false,
  },
  adminUsersHandler: {
    type: Object as PropType<AdminUsersHandler>,
    required: true,
  },
});

const emit = defineEmits(['logout']);

const userOptions = ref(false);
const changePasswordDialog = ref(false);
const expandedUserOptions = ref([]);

const { currentLanguage, supportedLanguages, selectLanguage, displayNames } = useLanguageHelper();

async function changeLanguage(langs: string[]) {
  await selectLanguage(langs[0]);
}
</script>
<style lang="scss" scoped>
.xrd-rail-options {
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
