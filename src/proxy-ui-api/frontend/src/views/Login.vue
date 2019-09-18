<template>
  <v-app class="app-custom">
    <v-content>
      <v-toolbar dark color="#202020" class="elevation-2">
        <v-img
          :src="require('../assets/xroad_logo_64.png')"
          height="64"
          width="128"
          max-height="64"
          max-width="128"
        ></v-img>
        <v-spacer></v-spacer>
      </v-toolbar>
      <v-container fluid fill-height>
        <v-layout align-center justify-center>
          <v-flex sm8 md4 class="set-width">
            <v-card flat>
              <v-toolbar flat class="login-form-toolbar">
                <v-toolbar-title class="login-form-toolbar-title">{{$t('login.logIn')}}</v-toolbar-title>
              </v-toolbar>
              <v-card-text>
                <v-form>
                  <v-text-field
                    id="username"
                    name="username"
                    :label="$t('login.username')"
                    type="text"
                    v-model="username"
                    :error-messages="errors.collect('username')"
                    data-vv-name="username"
                    @keyup.enter="submit"
                  ></v-text-field>
                  <v-text-field
                    id="password"
                    name="password"
                    :label="$t('login.password')"
                    type="password"
                    v-model="password"
                    :error-messages="errors.collect('password')"
                    data-vv-name="password"
                    @keyup.enter="submit"
                  ></v-text-field>
                </v-form>
              </v-card-text>
              <v-card-actions>
                <v-spacer></v-spacer>
                <v-btn
                  id="submit-button"
                  color="primary"
                  class="rounded-button"
                  @click="submit"
                  rounded
                  :disabled="isDisabled"
                  :loading="loading"
                >{{$t('login.logIn')}}</v-btn>
              </v-card-actions>
            </v-card>
          </v-flex>
        </v-layout>
      </v-container>
    </v-content>
  </v-app>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';

export default Vue.extend({
  name: 'login',
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
      const isValid = await this.$validator.validateAll();

      if (!isValid) {
        return;
      }

      const loginData = {
        username: this.username,
        password: this.password,
      };

      this.$validator.reset();
      this.loading = true;

      this.$store
        .dispatch('login', loginData)
        .then(
          (response) => {
            // Auth ok. Start phase 2 (fetch user data).
            this.fetchUserData();
          },
          (error) => {
            // Display invalid username/password error in inputs
            if (error.response && error.response.status === 401) {
              this.errors.add({
                field: 'username',
                msg: '',
              });
              this.errors.add({
                field: 'password',
                msg: this.$t('login.errorMsg401') as string,
              });

              // Clear inputs
              this.username = '';
              this.password = '';
              // Set inputs to error state
              this.errors.first('username');
              this.errors.first('password');
            }
            console.error(error);
            this.$bus.$emit('show-error', error.message);
          },
        )
        .finally(() => {
          // Clear loading state
          this.loading = false;
        });
    },
    async fetchUserData() {
      this.loading = true;
      this.$store
        .dispatch('fetchUserData')
        .then(
          (response) => {
            this.$router.replace({ name: RouteName.Clients });
          },
          (error) => {
            // Display error
            console.error(error);
            this.$bus.$emit('show-error', error.message);
          },
        )
        .finally(() => {
          // Clear loading state
          this.loading = false;
        });
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

