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
  <v-container class="fill-height w-100 pa-0 overflow-hidden" fluid>
    <v-row class="fill-height" no-gutters align-content-md="stretch" align-content="start">
      <v-col
        cols="12"
        md="5"
        class="logo-bg d-flex align-center justify-center position-relative"
        :class="{
          'horizontal-style': $vuetify.display.mdAndUp,
          'vertical-style': $vuetify.display.smAndDown,
        }"
      >
        <img :src="logoV" class="logo-v" alt="X-Road 8 Logo" />
        <img :src="logoH" class="logo-h" alt="X-Road 8 Logo" />
        <img :src="rocket" class="rocket" alt="X-Road 8 Rocket" />
        <img :src="trail1" class="trail1" alt="X-Road 8 Trail" />
        <img :src="trail2" class="trail2" alt="X-Road 8 Trail" />
      </v-col>
      <v-col cols="12" md="7">
        <v-row justify="end" align="center" no-gutters>
          <v-col cols="auto">
            <v-select
              :model-value="currentLanguage"
              :items="supportedLanguages"
              :item-props="langProps"
              class="text-primary mb-1"
              prepend-icon="language"
              variant="plain"
              density="compact"
              hide-details
              single-line
              @update:model-value="changeLanguage"
            />
          </v-col>
          <v-col cols="auto">
            <XrdThemeSwitcher class="mr-16 ml-3 my-3" size="x-small" />
          </v-col>
        </v-row>
        <v-row v-if="notifications.hasContextErrors.value" justify="center">
          <v-col cols="11">
            <XrdErrorNotifications :manager="notifications" />
          </v-col>
        </v-row>
        <v-row
          no-gutters
          justify="center"
          align="center"
          align-content="center"
          :class="{
            'fill-height': $vuetify.display.mdAndUp,
          }"
        >
          <v-col cols="11" sm="8" md="7" lg="6" xl="5">
            <v-card variant="text" :hover="false" class="login-form w-100 px-2">
              <v-card-title class="font-weight-bold title-page opacity-100 pt-0 pl-0">
                {{ $t('login.logIn') }}
              </v-card-title>
              <v-card-subtitle class="body-regular opacity-100 pt-0 pl-0">
                {{ $t('global.appTitle') }}
              </v-card-subtitle>
              <v-form>
                <v-text-field
                  id="username"
                  v-model="username"
                  v-bind="usernameAttrs"
                  name="username"
                  data-test="login-username-input"
                  class="xrd"
                  variant="underlined"
                  color="primary"
                  type="text"
                  :label="$t('fields.username')"
                  :error-messages="errors.username"
                  @keyup.enter="submit"
                />

                <v-text-field
                  id="password"
                  v-model="password"
                  v-bind="passwordAttrs"
                  data-test="login-password-input"
                  class="xrd"
                  name="password"
                  variant="underlined"
                  color="primary"
                  :label="$t('fields.password')"
                  :type="passwordType"
                  :error-messages="errors.password"
                  :append-inner-icon="passwordIcon"
                  @keyup.enter="submit"
                  @click:append-inner="changePasswordType"
                />
              </v-form>
              <v-card-actions class="pl-0 pr-0">
                <v-btn
                  id="submit-button"
                  data-test="login-button"
                  class="xrd body-large font-weight-medium"
                  variant="flat"
                  color="special"
                  rounded="xl"
                  size="large"
                  block
                  :disabled="isDisabled"
                  :loading="loading"
                  @click="submit"
                >
                  {{ $t('login.logIn') }}
                </v-btn>
              </v-card-actions>
            </v-card>
          </v-col>
        </v-row>
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useForm } from 'vee-validate';

import _logoVLight from '../assets/Logo-vertical-light.svg';
import _logoVDark from '../assets/Logo-vertical-dark.svg';
import _logoHLight from '../assets/Logo-horizontal-dark.svg';
import _logoHDark from '../assets/Logo-horizontal-light.svg';
import _rocket from '../assets/Rocket-trail.png';
import _trail1 from '../assets/Trail-1.png';
import _trail2 from '../assets/Trail-2.png';
import { useNotifications, useThemeHelper } from '../composables';
import { useLanguageHelper } from '../plugins/i18n';

import XrdErrorNotifications from '../components/XrdErrorNotifications.vue';
import { XrdThemeSwitcher } from '../components';

const rocket = _rocket;
const trail1 = _trail1;
const trail2 = _trail2;

const props = defineProps({
  loading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits<{
  login: [username: string, password: string];
}>();

defineExpose({ clearForm, addErrors });

const { isDark } = useThemeHelper();
const notifications = useNotifications();
const { currentLanguage, supportedLanguages, selectLanguage, displayNames } = useLanguageHelper();
const { meta, resetForm, setFieldError, errors, defineField } = useForm({
  validationSchema: {
    username: 'required',
    password: 'required',
  },
});

const logoV = computed(() => (isDark.value ? _logoVDark : _logoVLight));
const logoH = computed(() => (isDark.value ? _logoHDark : _logoHLight));

const PASSWORD = 'password';
const passwordType = ref(PASSWORD);
const passwordIcon = computed(() => (passwordType.value === PASSWORD ? 'visibility_off' : 'visibility'));

const isDisabled = computed(() => !meta.value.valid || props.loading);
const [username, usernameAttrs] = defineField('username');
const [password, passwordAttrs] = defineField('password');

function changePasswordType() {
  passwordType.value = passwordType.value === PASSWORD ? 'text' : PASSWORD;
}

function submit() {
  if (isDisabled.value) {
    return;
  }
  emit('login', username.value, password.value);
}

function clearForm() {
  resetForm();
}

function addErrors(...errors: string[]) {
  setFieldError('password', errors);
}

function langProps(lang: string) {
  return {
    title: displayNames.value.of(lang),
  };
}

async function changeLanguage(lang: string) {
  await selectLanguage(lang);
}
</script>

<style lang="scss" scoped>
.login-form {
}

.logo-bg {
  background: radial-gradient(rgb(var(--v-theme-login-start)), rgb(var(--v-theme-login))) !important;

  .rocket {
    position: absolute;
  }

  .trail1 {
    position: absolute;
  }

  .trail2 {
    position: absolute;
  }

  &.vertical-style {
    height: 180px;

    .logo-h {
      height: 48px;
    }

    .logo-v {
      display: none;
    }

    .rocket {
      bottom: -58px;
      right: 50%;
      width: 280px;
    }

    .trail1 {
      top: -130px;
      left: 50%;
      width: 262px;
    }

    .trail2 {
      display: none;
    }
  }

  &.horizontal-style {
    .logo-h {
      display: none;
    }

    .logo-v {
    }

    .rocket {
      top: -200px;
      right: -80px;
      width: 560px;
    }

    .trail1 {
      top: -200px;
      right: 170px;
      width: 512px;
      transform: rotate(90deg);
    }

    .trail2 {
      bottom: -185px;
      left: -200px;
      width: 512px;
      transform: rotate(135deg) scaleX(-1);
    }
  }

  .logo {
    width: 160px;
  }
}
</style>
