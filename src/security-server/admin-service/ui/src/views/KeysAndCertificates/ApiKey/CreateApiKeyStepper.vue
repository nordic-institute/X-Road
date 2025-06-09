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
  <v-container
    class="view-wrap ms-auto"
    data-test="create-api-key-stepper-view"
  >
    <xrd-sub-view-title
      :title="$t('apiKey.createApiKey.title')"
      :show-close="true"
      class="pa-4"
      @close="close"
    />
    <!-- eslint-disable-next-line vuetify/no-deprecated-components -->
    <v-stepper
      v-model="step"
      :alt-labels="true"
      class="wizard-stepper wizard-noshadow"
    >
      <v-stepper-header class="wizard-noshadow">
        <v-stepper-item :complete="step > 1" :value="1">
          {{ $t('apiKey.createApiKey.step.roles.name') }}
        </v-stepper-item>
        <v-divider />
        <v-stepper-item :complete="keyGenerated" :value="2">
          {{ $t('apiKey.createApiKey.step.keyDetails.name') }}
        </v-stepper-item>
      </v-stepper-header>
      <v-stepper-window>
        <v-stepper-window-item
          data-test="create-api-key-step-1"
          :value="1"
          class="pa-0 centered"
        >
          <div>
            <div class="wizard-step-form-content pt-6">
              <div class="wizard-row-wrap">
                <XrdFormLabel
                  :label-text="$t('apiKey.createApiKey.step.roles.selectRoles')"
                  :help-text="$t('apiKey.createApiKey.step.roles.description')"
                />
                <div class="wizard-form-input">
                  <div
                    v-for="role in availableRoles"
                    :key="role"
                    class="underline"
                  >
                    <v-checkbox
                      v-model="selectedRoles"
                      hide-details
                      :value="role"
                      :label="$t(`apiKey.role.${role}`)"
                      :data-test="`role-${role}-checkbox`"
                    />
                    <v-divider />
                  </div>
                </div>
              </div>
            </div>
            <div class="button-footer">
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
            </div>
          </div>
        </v-stepper-window-item>
        <v-stepper-window-item
          data-test="create-api-key-step-2"
          :value="2"
          class="pa-0"
        >
          <div>
            <div class="wizard-step-form-content pt-6">
              <div class="wizard-row-wrap">
                <v-table class="key-details">
                  <tbody>
                    <tr>
                      <td>
                        {{ $t('apiKey.createApiKey.step.keyDetails.apiKey') }}
                      </td>
                      <td data-test="created-apikey">{{ apiKey.key }}</td>
                      <td>
                        <xrd-button
                          v-if="apiKey.key"
                          class="float-right"
                          text
                          :outlined="false"
                          data-test="copy-key-button"
                          @click.prevent="copyKey()"
                        >
                          <v-icon
                            class="xrd-large-button-icon"
                            icon="mdi-content-copy"
                          />
                          {{ $t('action.copy') }}
                        </xrd-button>
                      </td>
                    </tr>
                    <tr>
                      <td>
                        {{ $t('apiKey.createApiKey.step.keyDetails.apiKeyID') }}
                      </td>
                      <td data-test="created-apikey-id" colspan="2">
                        {{ apiKey.id }}
                      </td>
                    </tr>
                    <tr>
                      <td>
                        {{
                          $t(
                            'apiKey.createApiKey.step.keyDetails.assignedRoles',
                          )
                        }}
                      </td>
                      <td colspan="2">{{ translatedRoles?.join(', ') }}</td>
                    </tr>
                  </tbody>
                  <tfoot>
                    <tr>
                      <td colspan="3" class="pt-12">
                        {{ $t('apiKey.createApiKey.step.keyDetails.note') }}
                        <v-spacer />
                        <xrd-button
                          data-test="create-key-button"
                          class="mt-6 float-right"
                          :disabled="keyGenerated"
                          :loading="generatingKey"
                          @click="generateKey"
                        >
                          <xrd-icon-base class="xrd-large-button-icon">
                            <XrdIconAdd />
                          </xrd-icon-base>
                          {{
                            $t(
                              'apiKey.createApiKey.step.keyDetails.createKeyButton',
                            )
                          }}
                        </xrd-button>
                      </td>
                    </tr>
                  </tfoot>
                </v-table>
              </div>
            </div>
            <div class="button-footer">
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
            </div>
          </div>
        </v-stepper-window-item>
      </v-stepper-window>
    </v-stepper>
  </v-container>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Roles } from '@/global';
import { ApiKey } from '@/global-types';
import * as api from '@/util/api';
import { helper } from '@niis/shared-ui';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';

export default defineComponent({
  name: 'CreateApiKeyStepper',
  data() {
    return {
      step: 1,
      generatingKey: false,
      selectedRoles: [] as string[],
      apiKey: {} as ApiKey,
    };
  },
  computed: {
    ...mapState(useUser, ['hasRole']),
    availableRoles(): string[] {
      return Roles.filter((role) => this.hasRole(role));
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
    ...mapActions(useNotifications, ['showSuccess']),
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
        helper.toClipboard(key);
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@use '@/assets/detail-views';
@use '@niis/shared-ui/src/assets/wizards';
@use '@niis/shared-ui/src/assets/colors';
</style>
