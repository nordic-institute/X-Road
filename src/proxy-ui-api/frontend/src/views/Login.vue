<template>
  <v-app>
    <v-content>
      <v-container fluid fill-height>
        <v-layout align-center justify-center>
          <v-flex xs12 sm8 md4>
            <v-card class="elevation-12">
              <v-toolbar dark color="primary">
                <v-toolbar-title>X-Road login</v-toolbar-title>
              </v-toolbar>
              <v-card-text>
                <v-form>
                  <v-text-field
                    prepend-icon="person"
                    name="login"
                    label="Username"
                    type="text"
                    v-model="username"
                    v-validate="'required'"
                    :error-messages="errors.collect('username')"
                    data-vv-name="username"
                    @keyup.enter="submit"
                  ></v-text-field>
                  <v-text-field
                    id="password"
                    prepend-icon="lock"
                    name="password"
                    label="Password"
                    type="password"
                    v-model="password"
                    v-validate="'required'"
                    :error-messages="errors.collect('password')"
                    data-vv-name="password"
                    @keyup.enter="submit"
                  ></v-text-field>
                </v-form>
              </v-card-text>
              <v-card-actions>
                <v-spacer></v-spacer>
                <v-btn color="primary" @click="submit" :disabled="loading">Login</v-btn>
              </v-card-actions>
              <v-progress-linear :indeterminate="true" v-if="loading"></v-progress-linear>
              <div id="padding" v-else />
            </v-card>
          </v-flex>
        </v-layout>
      </v-container>
    </v-content>
  </v-app>
</template>

<script lang="ts">
import Vue from "vue";

export default Vue.extend({
  name: "login",
  data() {
    return {
      loading: false,
      username: "user",
      password: "password"
    };
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
        password: this.password
      };

      this.$validator.reset();
      this.loading = true;

      this.$store
        .dispatch("login", loginData)
        .then(
          response => {
            this.$bus.$emit("show-success", "Logged in successfully");
          },
          error => {
            // Display invalid username/password error in inputs
            if (error.response.status === 401) {
              this.errors.add({
                field: "username",
                msg: ""
              });
              this.errors.add({
                field: "password",
                msg: "Wrong username or password"
              });

              this.errors.first("username");
              this.errors.first("password");
            }

            this.$bus.$emit("show-error", error.message);
          }
        )
        .finally(() => {
          // Clear loading state
          this.loading = false;
        });
    }
  }
});
</script>

<style lang="scss" scoped>
.v-progress-linear {
  margin: 0;
}

#padding {
  height: 7px;
}
</style>

