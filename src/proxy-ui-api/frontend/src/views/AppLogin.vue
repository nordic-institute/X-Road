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
      loading: false,
      username: 'xrd',
      password: 'secret',
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
          if (error.response && error.response.status === 401) {
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
          this.$store.dispatch('showErrorMessageRaw', error.message);
          // Clear loading state
          this.loading = false;
        },
      );
    },
    async fetchUserData() {
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

    async fetchInitializationData() {
      this.$store
        .dispatch('fetchInitializationStatus')
        .then(
          () => {
            if (this.$store.getters.needsInitialization) {
              // Check if the user has permission to initialize the server
              if (!this.$store.getters.hasPermission(Permissions.INIT_CONFIG)) {
                this.$store.dispatch(
                  'showErrorMessage',
                  'initialConfiguration.noPermission',
                );
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
