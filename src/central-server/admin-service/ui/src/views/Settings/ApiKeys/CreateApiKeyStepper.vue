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
  <xrd-sub-view-container data-test="create-api-key-stepper-view">
    <v-stepper
      v-model="step"
      :alt-labels="true"
      class="stepper mt-2"
      hide-actions
    >
      <xrd-sub-view-title
        :title="$t('apiKey.createApiKey.title')"
        :show-close="true"
        class="pa-4"
        @close="close"
      />
      <v-stepper-header>
        <v-stepper-item
          value="1"
          :complete="step > 0"
          :title="$t('apiKey.createApiKey.step.roles.name')"
        />
        <v-divider />
        <v-stepper-item
          value="2"
          :complete="keyGenerated"
          :title="$t('apiKey.createApiKey.step.keyDetails.name')"
        />
      </v-stepper-header>

      <v-stepper-window>
        <v-stepper-window-item value="1">
          <v-container class="wide-width">
            <v-row class="mt-4">
              <v-col
                ><h3>{{ $t('apiKey.createApiKey.step.roles.name') }}</h3></v-col
              >
            </v-row>
          </v-container>
          <v-container class="narrow-width">
            <v-row class="mb-5">
              <v-col>
                <h4>{{ $t('apiKey.createApiKey.step.roles.selectRoles') }}</h4>
                <br />
                {{ $t('apiKey.createApiKey.step.roles.description') }}
              </v-col>
              <v-col>
                <v-row v-for="role in availableRoles" :key="role" no-gutters>
                  <v-col class="underline">
                    <v-checkbox
                      v-model="selectedRoles"
                      hide-details
                      :value="role"
                      :label="$t(`apiKey.role.${role}`)"
                      :data-test="`role-${role}-checkbox`"
                    />
                  </v-col>
                </v-row>
              </v-col>
            </v-row>
          </v-container>

          <v-row class="button-footer mt-12" no-gutters>
            <xrd-button data-test="cancel-button" outlined @click="close">
              {{ $t('action.cancel') }}
            </xrd-button>

            <xrd-button
              data-test="next-button"
              :disabled="nextButtonDisabled"
              @click="step++"
            >
              {{ $t('action.next') }}
            </xrd-button>
          </v-row>
        </v-stepper-window-item>
        <v-stepper-window-item value="2">
          <v-container class="wide-width mb-8">
            <v-row class="mt-4">
              <v-col
                ><h3>
                  {{ $t('apiKey.createApiKey.step.keyDetails.name') }}
                </h3></v-col
              >
              <v-spacer></v-spacer>

              <xrd-button
                data-test="create-key-button"
                :disabled="keyGenerated"
                :loading="generatingKey"
                @click="generateKey"
              >
                <xrd-icon-base class="xrd-large-button-icon">
                  <XrdIconAdd />
                </xrd-icon-base>
                {{ $t('apiKey.createApiKey.step.keyDetails.createKeyButton') }}
              </xrd-button>
            </v-row>
          </v-container>
          <v-container class="narrow-width">
            <v-row class="underline">
              <v-col cols="6" sm="3" class="api-key-label">
                {{ $t('apiKey.createApiKey.step.keyDetails.apiKey') }}
              </v-col>
              <v-col cols="6" sm="9" class="action-row">
                <div data-test="created-apikey">{{ apiKey.key }}</div>

                <xrd-button
                  v-if="apiKey.key"
                  text
                  :outlined="false"
                  class="copy-button"
                  data-test="copy-key-button"
                  @click.prevent="copyKey()"
                >
                  <v-icon class="xrd-large-button-icon" icon="icon-copy" />
                  {{ $t('action.copy') }}
                </xrd-button>
              </v-col>
            </v-row>
            <v-row class="underline">
              <v-col cols="6" sm="3" class="api-key-label">
                {{ $t('apiKey.createApiKey.step.keyDetails.apiKeyID') }}
              </v-col>
              <v-col cols="6" sm="9" data-test="created-apikey-id">
                {{ apiKey.id }}
              </v-col>
            </v-row>
            <v-row class="underline">
              <v-col cols="6" sm="3" class="api-key-label">
                {{ $t('apiKey.createApiKey.step.keyDetails.assignedRoles') }}
              </v-col>
              <v-col cols="6" sm="9">
                {{ translatedRoles?.join(', ') }}
              </v-col>
            </v-row>
            <v-row class="mt-12">
              <v-col>
                {{ $t('apiKey.createApiKey.step.keyDetails.note') }}
              </v-col>
            </v-row>
          </v-container>
          <v-row class="button-footer mt-12" no-gutters>
            <xrd-button
              data-test="cancel-button"
              outlined
              :disabled="keyGenerated || generatingKey"
              @click="close"
            >
              {{ $t('action.cancel') }}
            </xrd-button>

            <xrd-button
              data-test="previous-button"
              outlined
              class="mr-5"
              :disabled="keyGenerated || generatingKey"
              @click="step--"
            >
              {{ $t('action.previous') }}
            </xrd-button>
            <xrd-button
              data-test="finish-button"
              :disabled="!keyGenerated"
              @click="close"
            >
              {{ $t('action.finish') }}
            </xrd-button>
          </v-row>
        </v-stepper-window-item>
      </v-stepper-window>
    </v-stepper>
  </xrd-sub-view-container>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Roles } from '@/global';
import { ApiKey } from '@/api-types';
import * as api from '@/util/api';
import { toClipboard } from '@/util/helpers';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';

export default defineComponent({
  data() {
    return {
      step: 0,
      generatingKey: false,
      selectedRoles: [] as string[],
      apiKey: {} as ApiKey,
    };
  },
  computed: {
    ...mapState(useUser, ['canAssignRole']),
    availableRoles(): string[] {
      return Roles.filter((role) => this.canAssignRole(role));
    },
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
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    close(): void {
      this.$router.back();
    },
    async generateKey() {
      this.generatingKey = true;
      api
        .post<ApiKey>('/api-keys', this.selectedRoles)
        .then((resp) => {
          this.apiKey = resp.data;
          this.showSuccess(this.$t('apiKey.createApiKey.success'));
        })
        .catch((error) => this.showError(error))
        .finally(() => (this.generatingKey = false));
    },
    copyKey(): void {
      const key = this.apiKey.key;
      if (key) {
        toClipboard(key);
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@use '@/assets/forms' as *;
@use '@/assets/colors';

:deep(.v-stepper-window) {
  margin-right: 0;
  margin-left: 0;
  margin-bottom: 0;
}

:deep(.v-stepper-header) {
  width: 50%;
  margin-right: auto;
  margin-left: auto;
  box-shadow: none;
}

.stepper {
  box-shadow: unset;
  box-shadow: colors.$DefaultShadow;
}

.underline {
  border-bottom: solid 1px colors.$WarmGrey30;
}

.api-key-label {
  font-weight: 500;
}

.wide-width {
  max-width: 1040px;
}

.narrow-width {
  max-width: 840px;
}

h3 {
  color: colors.$Black100;
  font-size: 18px;
  font-weight: 700;
}

h4 {
  color: colors.$Black100;
  font-size: 14px;
  font-weight: 700;
}

.action-row {
  display: flex;
  justify-content: space-between;

  .copy-button {
    margin-top: -10px;
    margin-bottom: -10px;
  }
}
</style>
