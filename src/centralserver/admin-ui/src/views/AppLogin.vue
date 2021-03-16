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
  <v-container fluid fill-height class="login-view-wrap">
    <div class="graphics">
      <v-img
        :src="require('../assets/xroad7_large.svg')"
        height="195"
        width="144"
        max-height="195"
        max-width="144"
        class="xrd-logo"
      ></v-img>
    </div>

    <v-layout align-center justify-center>
      <v-flex class="set-width">
        <v-card flat>
          <v-toolbar flat class="login-form-toolbar">
            <div class="title-wrap">
              <div class="login-form-toolbar-title">
                {{ $t('login.logIn') }}
              </div>
              <div class="sub-title">X-Road Central Server</div>
            </div>
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
                    outlined
                    :label="$t('fields.username')"
                    :error-messages="errors"
                    type="text"
                    v-model="username"
                    @keyup.enter="submit"
                    autofocus
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
                    outlined
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
          <v-card-actions class="px-4">
            <xrd-button
              id="submit-button"
              color="primary"
              gradient
              block
              large
              @click="submit"
              :min_width="120"
              rounded
              :disabled="isDisabled"
              :loading="loading"
              >{{ $t('login.logIn') }}</xrd-button
            >
          </v-card-actions>
        </v-card>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';
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

      this.$refs.form.reset();
      this.loading = true;

      this.$router.replace({
        name: 'base',
      });
    },
    fetchUserData() {
      // do something later
    },

    fetchInitializationData() {
      // do something later
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.graphics {
  height: 100%;
  width: 40%;
  max-width: 576px; // width of the backround image
  background-image: url('../assets/background.png');
  background-size: cover;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.login-view-wrap {
  background-color: white;
  padding: 0;
}

.title-wrap {
  display: flex;
  flex-direction: column;
}

.login-form-toolbar {
  background-color: white;
  margin-bottom: 30px;
  padding-left: 0;
}

.login-form-toolbar-title {
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

.set-width {
  max-width: 420px;
}
</style>
