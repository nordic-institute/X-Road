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
  <v-container class="xrd-view-common justify-center wrapper">
    <v-stepper :alt-labels="true" v-model="step" class="stepper mt-2">
      <sub-view-title
        :title="$t('apiKey.createApiKey.title')"
        :show-close="true"
        @close="close"
        class="pa-4"
      ></sub-view-title>

      <v-stepper-header class="stepper-header">
        <v-stepper-step :complete="step > 1" step="1">{{
          $t('apiKey.createApiKey.step.roles.name')
        }}</v-stepper-step>
        <v-divider />
        <v-stepper-step :complete="keyGenerated" step="2">{{
          $t('apiKey.createApiKey.step.keyDetails.name')
        }}</v-stepper-step>
      </v-stepper-header>
      <v-stepper-items>
        <v-stepper-content step="1" class="pa-0">
          <div class="px-6">
            <v-row class="mb-5">
              <v-col>
                <h3>{{ $t('apiKey.createApiKey.step.roles.description') }}</h3>
              </v-col>
            </v-row>
            <v-row no-gutters v-for="role in roles" :key="role">
              <v-col class="checkbox-wrapper">
                <v-checkbox
                  v-model="selectedRoles"
                  height="10px"
                  :value="role"
                  :label="$t(`apiKey.role.${role}`)"
                />
              </v-col>
            </v-row>
          </div>
          <v-row class="button-footer mt-12" no-gutters>
            <large-button outlined @click="close">
              {{ $t('action.cancel') }}
            </large-button>

            <large-button :disabled="nextButtonDisabled" @click="step++">
              {{ $t('action.next') }}
            </large-button>
          </v-row>
        </v-stepper-content>
        <v-stepper-content step="2" class="pa-0">
          <div class="px-6">
            <v-row>
              <v-col class="text-right">
                <large-button
                  :disabled="keyGenerated"
                  :loading="generatingKey"
                  @click="generateKey"
                >
                  {{
                    $t('apiKey.createApiKey.step.keyDetails.createKeyButton')
                  }}
                </large-button>
              </v-col>
            </v-row>
            <v-row>
              <v-col cols="6" sm="3" class="api-key-label">
                {{ $t('apiKey.createApiKey.step.keyDetails.apiKey') }}
              </v-col>
              <v-col cols="6" sm="9">
                {{ apiKey.key }}
              </v-col>
            </v-row>
            <v-row>
              <v-col cols="6" sm="3" class="api-key-label">
                {{ $t('apiKey.createApiKey.step.keyDetails.apiKeyID') }}
              </v-col>
              <v-col cols="6" sm="9">
                {{ apiKey.id }}
              </v-col>
            </v-row>
            <v-row>
              <v-col cols="6" sm="3" class="api-key-label">
                {{ $t('apiKey.createApiKey.step.keyDetails.assignedRoles') }}
              </v-col>
              <v-col cols="6" sm="9">
                {{ translatedRoles | commaSeparate }}
              </v-col>
            </v-row>
            <v-row class="mt-12">
              <v-col>
                {{ $t('apiKey.createApiKey.step.keyDetails.note') }}
              </v-col>
            </v-row>
          </div>
          <v-row class="button-footer mt-12" no-gutters>
            <large-button
              outlined
              @click="close"
              :disabled="keyGenerated || generatingKey"
            >
              {{ $t('action.cancel') }}
            </large-button>

            <large-button
              outlined
              @click="step--"
              class="mr-5"
              :disabled="keyGenerated || generatingKey"
            >
              {{ $t('action.previous') }}
            </large-button>
            <large-button :disabled="!keyGenerated" @click="close">
              {{ $t('action.finish') }}
            </large-button>
          </v-row>
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { Roles } from '@/global';
import { ApiKey } from '@/global-types';
import * as api from '@/util/api';

export default Vue.extend({
  name: 'CreateApiKeyStepper',
  data() {
    return {
      step: 1,
      roles: Roles,
      generatingKey: false,
      selectedRoles: [] as string[],
      apiKey: {} as ApiKey,
    };
  },
  computed: {
    nextButtonDisabled(): boolean {
      return this.selectedRoles.length === 0;
    },
    translatedRoles(): string[] {
      return !this.apiKey.roles
        ? []
        : this.apiKey.roles.map(
            (role) => this.$t(`apiKey.role.${role}`) as string,
          );
    },
    keyGenerated(): boolean {
      return this.apiKey.key !== undefined;
    },
  },
  methods: {
    close(): void {
      this.$router.back();
    },
    async generateKey() {
      this.generatingKey = true;
      api
        .post<ApiKey>('/api-keys', this.selectedRoles)
        .then((resp) => {
          this.apiKey = resp.data;
          this.$store.dispatch('showSuccess', 'apiKey.createApiKey.success');
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => (this.generatingKey = false));
    },
  },
});
</script>

<style scoped lang="scss">
@import '~styles/detail-views';
@import '~styles/wizards';
.wrapper {
  max-width: 850px;
  height: 100%;
  width: 100%;
  color: $XRoad-Grey60;
}
.stepper {
  box-shadow: unset;
  box-shadow: $XRoad-DefaultShadow;
}
.stepper-header {
  box-shadow: unset;
  width: 50%;
  margin: auto;
}
.stepper-item-footer {
  margin-top: 20px;
  padding-top: 30px;
  border-top: 1px solid $XRoad-Grey40;
}
.checkbox-wrapper {
  border-bottom: solid 1px $XRoad-Grey10;
}
.api-key-label {
  font-weight: 500;
}
h3 {
  color: $XRoad-Grey60;
  font-weight: 400;
}
</style>
