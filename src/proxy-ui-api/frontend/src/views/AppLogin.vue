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
  <v-container fluid fill-height>
    <v-layout align-center justify-center>
      <v-flex sm8 md4 class="set-width">
        <v-card flat>
          <v-toolbar flat class="login-form-toolbar">
            <v-toolbar-title class="login-form-toolbar-title">{{
              $t('login.logIn')
            }}</v-toolbar-title>
          </v-toolbar>
          <v-card-text>
            <v-form>
              <ValidationObserver ref="form">
                <ValidationProvider
                  name="username"
                  rules="required"
                  v-slot="{ errors }"
                >
                  <v-text-field
                    id="username"
                    name="username"
                    :label="$t('fields.username')"
                    :error-messages="errors"
                    type="text"
                    v-model="username"
                    @keyup.enter="submit"
                  ></v-text-field>
                </ValidationProvider>

                <ValidationProvider
                  name="password"
                  rules="required"
                  v-slot="{ errors }"
                >
                  <v-text-field
                    id="password"
                    name="password"
                    :label="$t('fields.password')"
                    :error-messages="errors"
                    type="password"
                    v-model="password"
                    @keyup.enter="submit"
                  ></v-text-field>
                </ValidationProvider>
              </ValidationObserver>
            </v-form>
          </v-card-text>
          <v-card-actions>
            <v-spacer></v-spacer>
            <v-btn
              id="submit-button"
              color="primary"
              class="rounded-button"
              @click="submit"
              min-width="120"
              rounded
              :disabled="isDisabled"
              :loading="loading"
              >{{ $t('login.logIn') }}
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';
import { RouteName, Permissions } from '@/global';
import { ValidationProvider, ValidationObserver } from 'vee-validate';

export default (Vue as VueConstructor<
  Vue & {
    $refs: {
      form: InstanceType<typeof ValidationObserver>;
    };
  }
>).extend({
  name: 'login',
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  data() {
    return {
      loading: false as boolean,
      username: '' as string,
      password: '' as string,
    };
  },
  computed: {
    isDisabled() {
      if (
        this.username.length < 1 ||
        this.password.length < 1 ||
        this.loading
      ) {
        return true;
      }
      return false;
    },
  },
  methods: {
    async submit() {
      // Validate inputs

      const isValid = await this.$refs.form.validate();

      if (!isValid) {
        return;
      }

      const loginData = {
        username: this.username,
        password: this.password,
      };

      this.$refs.form.reset();
      this.loading = true;

      this.$store.dispatch('login', loginData).then(
        () => {
          // Auth ok. Start phase 2 (fetch user data and current security server info).
          this.fetchUserData();
          this.fetchSecurityServerVersion();
        },
        (error) => {
          // Display invalid username/password error in inputs
          if (error?.response?.status === 401) {
            // Clear inputs
            this.username = '';
            this.password = '';
            this.$refs.form.reset();

            // The whole view needs to be rendered so the "required" rule doesn't block
            // "wrong unsername or password" error in inputs
            this.$nextTick(() => {
              // Set inputs to error state
              this.$refs.form.setErrors({
                username: [''],
                password: [this.$t('login.errorMsg401') as string],
              });
            });
          }
          this.$store.dispatch('showErrorMessageCode', 'login.generalError');
          // Clear loading state
          this.loading = false;
        },
      );
    },
    fetchUserData() {
      this.loading = true;
      this.$store.dispatch('fetchUserData').then(
        () => {
          // Check if initialization is needed
          this.fetchInitializationData();
        },
        (error) => {
          // Display error
          this.$store.dispatch('showErrorMessageRaw', error.message);
          this.loading = false;
        },
      );
    },

    fetchInitializationData() {
      this.$store
        .dispatch('fetchInitializationStatus')
        .then(
          () => {
            if (this.$store.getters.needsInitialization) {
              // Check if the user has permission to initialize the server
              if (!this.$store.getters.hasPermission(Permissions.INIT_CONFIG)) {
                this.$store.dispatch(
                  'showErrorMessageCode',
                  'initialConfiguration.noPermission',
                );
                // Logout without page refresh
                this.$store.dispatch('logout', false);
                // Clear inputs
                this.username = '';
                this.password = '';
                this.$refs.form.reset();

                return;
              }
              this.$router.replace({ name: RouteName.InitialConfiguration });
            } else {
              this.fetchCurrentSecurityServer();
              this.$router.replace({ name: RouteName.Clients });
            }
          },
          (error) => {
            // Display error
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          // Clear loading state
          this.loading = false;
        });
    },

    async fetchCurrentSecurityServer() {
      this.$store.dispatch('fetchCurrentSecurityServer').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
    async fetchSecurityServerVersion() {
      this.$store
        .dispatch('fetchSecurityServerVersion')
        .catch((error) => this.$store.dispatch('showError', error));
    },
  },
});
</script>

<style lang="scss" scoped>
.app-custom {
  background-color: white;
}

.login-form-toolbar {
  background-color: white;
  border-bottom: 1px #9b9b9b solid;
  margin-bottom: 30px;
  padding-left: 0;
}

.login-form-toolbar-title {
  margin-left: 0;
  color: #4a4a4a;
  font-size: 36px;
  font-weight: 300;
  line-height: 44px;
  margin-left: -24px;
}

.set-width {
  max-width: 420px;
}
</style>
