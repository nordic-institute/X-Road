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
  <v-container fluid class="login-view-wrap fill-height">
    <slot name="top" />
    <XrdLanguageDropdown class="language-dropdown" />
    <v-row no-gutters class="fill-height">
      <v-col cols="3">
        <div class="graphics">
          <v-img :src="xroad7LargeUrl" height="195" width="144" max-height="195" max-width="144" class="xrd-logo"></v-img>
        </div>
      </v-col>
      <v-col cols="9" align-self="center">
        <v-container class="set-width">
          <v-card variant="flat">
            <v-card-item class="title-wrap">
              <v-card-title class="login-form-title">
                {{ $t('login.logIn') }}
              </v-card-title>
              <v-card-subtitle class="sub-title">
                {{ $t('global.appTitle') }}
              </v-card-subtitle>
            </v-card-item>

            <v-card-text>
              <v-form>
                <v-text-field
                  id="username"
                  v-model="username"
                  v-bind="usernameAttrs"
                  name="username"
                  data-test="login-username-input"
                  variant="outlined"
                  :label="$t('fields.username')"
                  :error-messages="errors.username"
                  type="text"
                  autofocus
                  @keyup.enter="submit"
                ></v-text-field>

                <v-text-field
                  id="password"
                  v-model="password"
                  v-bind="passwordAttrs"
                  name="password"
                  data-test="login-password-input"
                  variant="outlined"
                  :label="$t('fields.password')"
                  :error-messages="errors.password"
                  type="password"
                  @keyup.enter="submit"
                ></v-text-field>
              </v-form>
            </v-card-text>
            <v-card-actions class="px-4">
              <xrd-button
                id="submit-button"
                color="primary"
                gradient
                block
                data-test="login-button"
                :min_width="120"
                :disabled="isDisabled"
                :loading="loading"
                @click="submit"
              >
                {{ $t('login.logIn') }}
              </xrd-button>
            </v-card-actions>
          </v-card>
        </v-container>
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts" setup>
import xroad7Large from '../assets/xroad7_large.svg';
import { computed } from 'vue';
import { useForm } from 'vee-validate';
import { XrdLanguageDropdown } from '@niis/shared-ui';

const xroad7LargeUrl = xroad7Large;

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

const isDisabled = computed(() => !meta.value.valid || props.loading);
const [username, usernameAttrs] = defineField('username');
const [password, passwordAttrs] = defineField('password');

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
@use '../assets/colors';

.v-text-field {
  margin-bottom: 6px;
}

.alerts {
  top: 40px;
  left: 0;
  right: 0;
  margin-left: auto;
  margin-right: auto;
  z-index: 100;
  position: absolute;
}

.language-dropdown {
  position: absolute;
  top: 10px;
  right: 10px;
}

.login-view-wrap {
  background-color: white;
  padding: 0;

  .graphics {
    height: 100%;
    max-width: 576px; // width of the backround image
    background-image: url('../assets/background.png');
    background-size: cover;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
  }

  .set-width {
    max-width: 420px;

    .title-wrap {
      margin-bottom: 30px;

      .login-form-title {
        margin-left: 0;
        color: #252121;
        font-style: normal;
        font-weight: bold;
        font-size: 40px;
        line-height: 54px;
      }

      .sub-title {
        font-style: normal;
        font-weight: normal;
        font-size: colors.$DefaultFontSize;
        line-height: 19px;
      }
    }
  }
}
</style>
