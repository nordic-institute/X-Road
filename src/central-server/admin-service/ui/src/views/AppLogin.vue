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
                  v-bind="username"
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
                  v-bind="password"
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

<script lang="ts">
import xroad7Large from '@/assets/xroad7_large.svg';
import { defineComponent } from 'vue';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { useSystem } from '@/store/modules/system';
import { useForm } from 'vee-validate';
import AlertsContainer from '@/components/ui/AlertsContainer.vue';
import { swallowRedirectedNavigationError } from '@/util/helpers';
import axios from 'axios';

export default defineComponent({
  components: {
    AlertsContainer,
  },
  setup() {
    const {
      meta,
      defineComponentBinds,
      resetForm,
      setFieldError,
      errors,
      values,
    } = useForm({
      validationSchema: {
        username: 'required',
        password: 'required',
      },
    });
    const username = defineComponentBinds('username');
    const password = defineComponentBinds('password');
    return {
      meta,
      username,
      password,
      resetForm,
      setFieldError,
      errors,
      values,
    };
  },
  data() {
    return {
      xroad7Large,
      loading: false as boolean,
    };
  },
  computed: {
    ...mapState(useUser, ['getFirstAllowedTab']),
    isDisabled() {
      // beware: simplified one-liner fails at runtime
      if (
        (this.values.username?.length | 0) < 1 ||
        (this.values.password?.length | 0) < 1 ||
        this.loading
      ) {
        return true;
      } else {
        return false;
      }
    },
  },
  methods: {
    ...mapActions(useUser, ['login', 'fetchUserData']),
    ...mapActions(useNotifications, [
      'showError',
      'showErrorMessage',
      'resetNotifications',
    ]),
    ...mapActions(useSystem, ['fetchSystemStatus', 'fetchServerVersion']),
    async submit() {
      if (this.isDisabled) {
        return;
      }
      // Clear old error notifications (if they exist) before submit
      await this.resetNotifications();

      // Validate inputs
      const isValid = this.meta.valid;
      if (!isValid) {
        return;
      }
      const loginData = {
        username: this.values.username,
        password: this.values.password,
      };

      this.loading = true;

      try {
        await this.login(loginData);
      } catch (error: unknown) {
        // Display invalid username/password error in inputs
        if (axios.isAxiosError(error) && error?.response?.status === 401) {
          // Clear inputs
          this.resetForm();

          this.setFieldError('password', this.$t('login.errorMsg401'));
          this.showErrorMessage(this.$t('login.generalError'));
        } else {
          this.showError(error);
        }
        this.loading = false;
        return;
      }
      try {
        await this.requestUserData();
        await this.fetchServerVersion();
        await this.fetchSystemStatus();
        await this.routeToMembersPage();
      } catch (error) {
        this.showError(error);
        this.loading = false;
      }
    },
    async requestUserData() {
      this.resetForm();
      this.loading = true;
      return this.fetchUserData();
    },
    async routeToMembersPage() {
      this.$router
        .replace(this.getFirstAllowedTab.to)
        .catch(swallowRedirectedNavigationError);
      this.loading = false;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';

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
        font-size: $XRoad-DefaultFontSize;
        line-height: 19px;
      }
    }
  }
}
</style>
