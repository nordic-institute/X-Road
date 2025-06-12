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
    <alerts-container class="alerts" />
    <language-dropdown class="language-dropdown"/>
    <v-row no-gutters class="fill-height">
      <v-col cols="3">
        <div class="graphics">
          <v-img
            :src="xroad7Large"
            height="195"
            width="144"
            max-height="195"
            max-width="144"
            class="xrd-logo"
          ></v-img>
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
                  variant="outlined"
                  :label="$t('fields.username')"
                  type="text"
                  autofocus
                  data-test="login-username-input"
                  @keyup.enter="submit"
                ></v-text-field>

                <v-text-field
                  id="password"
                  v-model="password"
                  v-bind="passwordAttrs"
                  variant="outlined"
                  :label="$t('fields.password')"
                  type="password"
                  data-test="login-password-input"
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
                :disabled="loading || !meta.valid"
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

<script lang="ts">
import { Permissions, RouteName } from '@/global';
import AlertsContainer from '@/components/ui/AlertsContainer.vue';
import axios, { AxiosError } from 'axios';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useSystem } from '@/store/modules/system';
import { useNotifications } from '@/store/modules/notifications';
import { defineComponent } from 'vue';
import xroad7Large from '@/assets/xroad7_large.svg';
import { PublicPathState, useForm } from 'vee-validate';
import LanguageDropdown from '@/components/layout/LanguageDropdown.vue';

export default defineComponent({
  components: {
    LanguageDropdown,
    AlertsContainer,
  },
  setup() {
    const { meta, defineField, resetForm, setFieldError, errors, values } =
      useForm({
        validationSchema: {
          username: 'required|max:255',
          password: 'required|max:255',
        },
        initialValues: {
          username: '',
          password: '',
        },
      });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const [username, usernameAttrs] = defineField('username', componentConfig);
    const [password, passwordAttrs] = defineField('password', componentConfig);
    return {
      meta,
      username,
      usernameAttrs,
      password,
      passwordAttrs,
      resetForm,
      setFieldError,
      errors,
      values,
    };
  },
  data() {
    return {
      loading: false as boolean,
      xroad7Large,
    };
  },
  computed: {
    ...mapState(useUser, [
      'hasPermission',
      'firstAllowedTab',
      'hasInitState',
      'needsInitialization',
    ]),
  },
  methods: {
    ...mapActions(useUser, [
      'loginUser',
      'logoutUser',
      'fetchInitializationStatus',
      'fetchUserData',
      'fetchCurrentSecurityServer',
      'clearAuth',
    ]),
    ...mapActions(useSystem, [
      'fetchSecurityServerVersion',
      'fetchSecurityServerNodeType',
      'fetchAuthenticationProviderType',
      'clearSystemStore',
    ]),
    ...mapActions(useNotifications, [
      'showError',
      'showErrorMessage',
      'clearErrorNotifications',
    ]),
    async submit() {
      // Clear error notifications when route is changed
      this.clearErrorNotifications();

      /* Clear user data so there is nothing left from previous sessions.
       For example user has closed browser tab without loggin out > user data is left in browser local storage */
      this.clearAuth();
      this.clearSystemStore();

      // Validate inputs
      const isValid = this.meta.valid;
      if (!isValid) {
        return;
      }
      const loginData = {
        username: this.values.username,
        password: this.values.password,
      };

      this.resetForm();
      this.loading = true;

      try {
        await this.loginUser(loginData);
      } catch (error) {
        if (axios.isAxiosError(error)) {
          // Display invalid username/password error in inputs
          if (error?.response?.status === 401) {
            // Clear inputs
            this.resetForm();

            this.setFieldError('password', this.$t('login.errorMsg401'));
          }
          this.showErrorMessage(this.$t('login.generalError'));
        } else {
          if (error instanceof Error) {
            this.showErrorMessage(error.message);
          } else {
            throw error;
          }
        }
      }

      // Auth ok. Start phase 2 (fetch user data and current security server info).

      try {
        await this.fetchUserData();
        await this.fetchInitializationData(); // Used to be inside fetchUserData()
        await this.fetchSecurityServerVersion();
        await this.fetchSecurityServerNodeType();
        await this.fetchAuthenticationProviderType();
      } catch (error) {
        this.showError(error as AxiosError);
      }

      // Clear loading state
      this.loading = false;
    },

    async fetchInitializationData() {
      const redirectToLogin = async () => {
        // Logout without page refresh
        await this.logoutUser(false);
        // Clear inputs
        this.resetForm();
      };

      await this.fetchInitializationStatus();
      await this.fetchSecurityServerNodeType();
      if (!this.hasInitState) {
        this.showErrorMessage(
          this.$t('initialConfiguration.noInitializationStatus'),
        );
        await redirectToLogin();
      } else if (this.needsInitialization) {
        // Check if the user has permission to initialize the server
        if (!this.hasPermission(Permissions.INIT_CONFIG)) {
          await redirectToLogin();
          throw new Error(
            this.$t('initialConfiguration.noPermission') as string,
          );
        }
        await this.$router.replace({ name: RouteName.InitialConfiguration });
      } else {
        // No need to initialise, proceed to "main view"
        await this.fetchCurrentSecurityServer();
        await this.$router.replace(this.firstAllowedTab.to);
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@use '@/assets/colors';

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
