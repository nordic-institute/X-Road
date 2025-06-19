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
  <v-container fluid class="fill-height ma-0 pa-0">
    <v-row class="fill-height flex-nowrap">
      <v-col cols="4" class="logo-bg fill-height d-flex align-center justify-center position-relative">
        <img :src="logo" alt="X-Road 8 Logo" />
        <img :src="rocket" class="rocket" alt="X-Road 8 Rocket" />
        <img :src="trail1" class="trail1" alt="X-Road 8 Trail" />
        <img :src="trail2" class="trail2" alt="X-Road 8 Trail" />
      </v-col>
      <v-col class="fill-height d-flex align-center justify-center">
        <v-card color="on-surface" variant="text" :hover="false" class="login-form">
          <v-card-title class="font-weight-bold title-page opacity-100">
            {{ $t('login.logIn') }}
          </v-card-title>
          <v-card-subtitle class="body-regular opacity-100">
            {{ $t('global.appTitle') }}
          </v-card-subtitle>

          <v-card-item>
            <v-form>
              <v-text-field
                id="username"
                v-model="username"
                v-bind="usernameAttrs"
                name="username"
                data-test="login-username-input"
                variant="underlined"
                :label="$t('fields.username')"
                :error-messages="errors.username"
                type="text"
                @keyup.enter="submit"
              />

              <v-text-field
                id="password"
                v-model="password"
                v-bind="passwordAttrs"
                data-test="login-password-input"
                name="password"
                variant="underlined"
                :label="$t('fields.password')"
                :type="passwordType"
                :error-messages="errors.password"
                :append-inner-icon="passwordIcon"
                @keyup.enter="submit"
                @click:append-inner="changePasswordType"
              />
            </v-form>
          </v-card-item>
          <v-card-actions class="px-4">
            <v-btn
              id="submit-button"
              class="body-large font-weight-medium"
              variant="flat"
              color="special"
              rounded="xl"
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
  </v-container>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useForm } from 'vee-validate';
import _logoLight from '../assets/xrd8/Logo-vertical-light.png';
import _rocket from '../assets/xrd8/Rocket-trail.png';
import _trail1 from '../assets/xrd8/Trail-1.png';
import _trail2 from '../assets/xrd8/Trail-2.png';

const logo = _logoLight;
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

const { meta, resetForm, setFieldError, errors, defineField } = useForm({
  validationSchema: {
    username: 'required',
    password: 'required',
  },
});

const PASSWORD = 'password';
const passwordType = ref(PASSWORD);
const passwordIcon = computed(() => (passwordType.value === PASSWORD ? 'msr-visibility-off' : 'msr-visibility'));

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
</script>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/xrd8/colors';

.login-form {
  width: 434px;
}

.logo-bg {
  background: radial-gradient(colors.$Maroon600, colors.$Maroon800);
  max-width: 600px;

  .rocket {
    position: absolute;
    top: -200px;
    right: -80px;
    width: 560px;
  }

  .trail1 {
    position: absolute;
    top: -200px;
    right: 170px;
    width: 512px;
    transform: rotate(90deg);
  }

  .trail2 {
    position: absolute;
    bottom: -185px;
    right: 300px;
    width: 512px;
    transform: rotate(135deg) scaleX(-1);
  }

  .logo {
    width: 160px;
  }
}
</style>
