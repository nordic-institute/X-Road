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
  <XrdElevatedViewSimple data-test="create-api-key-stepper-view" title="apiKey.createApiKey.title" go-back-on-close>
    <template #bellow-header>
      <XrdBreadcrumbs v-if="breadcrumbs && breadcrumbs.length > 0" :breadcrumbs="breadcrumbs" />
    </template>
    <XrdWizard v-model="step">
      <template #header-items>
        <v-stepper-item :value="1" :complete="step > 1" :title="$t('apiKey.createApiKey.step.roles.name')" />
        <v-divider />
        <v-stepper-item :value="2" :title="$t('apiKey.createApiKey.step.keyDetails.name')" />
      </template>
      <v-stepper-window-item :value="1">
        <XrdWizardStep title="apiKey.createApiKey.step.roles.selectRoles" sub-title="apiKey.createApiKey.step.roles.description">
          <XrdFormBlock>
            <div v-for="(role, idx) in availableRoles" :key="role" :class="{ 'mb-5': idx < availableRoles.length - 1 }">
              <v-checkbox
                v-model="selectedRoles"
                class="xrd"
                density="compact"
                hide-details
                :value="role"
                :label="$t(`apiKey.role.${role}`)"
                :data-test="`role-${role}-checkbox`"
              />
            </div>
          </XrdFormBlock>
          <template #footer>
            <XrdBtn data-test="cancel-button" variant="text" text="action.cancel" @click="close" />
            <v-spacer />
            <XrdBtn
              data-test="next-button"
              variant="flat"
              prepend-icon="add_circle"
              text="apiKey.createApiKey.step.keyDetails.createKeyButton"
              :loading="generatingKey"
              :disabled="nextButtonDisabled"
              @click="generateKey"
            />
          </template>
        </XrdWizardStep>
      </v-stepper-window-item>
      <v-stepper-window-item :value="2">
        <XrdWizardStep title="apiKey.createApiKey.step.keyDetails.note" sub-title="apiKey.createApiKey.step.keyDetails.noteDetail">
          <XrdFormBlock>
            <v-text-field
              data-test="created-apikey"
              class="xrd mb-4"
              variant="plain"
              readonly
              hide-details
              :model-value="apiKey.key"
              :label="$t('apiKey.createApiKey.step.keyDetails.apiKey')"
            >
              <template #append-inner>
                <v-btn
                  class="xrd"
                  prepend-icon="content_copy"
                  size="x-small"
                  rounded="xl"
                  color="tertiary"
                  variant="outlined"
                  @click.prevent="copyKey()"
                >
                  {{ $t('action.copy') }}
                </v-btn>
              </template>
            </v-text-field>
            <v-text-field
              data-test="created-apikey-id"
              class="xrd mb-4"
              variant="plain"
              readonly
              hide-details
              :model-value="apiKey.id"
              :label="$t('apiKey.createApiKey.step.keyDetails.apiKeyID')"
            />
            <v-text-field
              class="xrd"
              variant="plain"
              readonly
              hide-details
              :model-value="translatedRoles?.join(', ')"
              :label="$t('apiKey.createApiKey.step.keyDetails.assignedRoles')"
            />
          </XrdFormBlock>
          <template #footer>
            <v-spacer />
            <XrdBtn data-test="finish-button" variant="flat" prepend-icon="check" text="action.finish" @click="close" />
          </template>
        </XrdWizardStep>
      </v-stepper-window-item>
    </XrdWizard>
  </XrdElevatedViewSimple>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';

import { XrdBtn, XrdFormBlock, XrdBreadcrumbs } from '../../components';
import { XrdWizard, XrdWizardStep } from '../../components/wizard';
import { XrdElevatedViewSimple } from '../../layouts';
import { useNotifications } from '../../composables';
import { toClipboard } from '../../utils';

import { ApiKeysHandler, ApiKey } from '../../types';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';

export default defineComponent({
  components: {
    XrdBreadcrumbs,
    XrdElevatedViewSimple,
    XrdBtn,
    XrdWizard,
    XrdWizardStep,
    XrdFormBlock,
  },
  props: {
    handler: {
      type: Object as PropType<ApiKeysHandler>,
      required: true,
    },
    apiKeyListRouteName: {
      type: String,
      required: true,
    },
    breadcrumbs: {
      type: Array as PropType<BreadcrumbItem[]>,
      default: () => [],
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      step: 1,
      generatingKey: false,
      selectedRoles: [] as string[],
      apiKey: {} as ApiKey,
    };
  },
  computed: {
    availableRoles(): string[] {
      return this.handler.availableRoles().filter((role) => this.handler.canAssignRole(role));
    },
    nextButtonDisabled(): boolean {
      return this.selectedRoles.length === 0;
    },
    translatedRoles(): string[] {
      return !this.apiKey.roles ? [] : this.apiKey.roles.map((role) => this.$t(`apiKey.role.${role}`) as string);
    },
  },
  methods: {
    close(): void {
      this.$router.push({ name: this.apiKeyListRouteName });
    },
    async generateKey() {
      this.generatingKey = true;
      this.handler
        .addApiKey(this.selectedRoles)
        .then((apiKey) => {
          this.apiKey = apiKey;
          this.addSuccessMessage('apiKey.createApiKey.success');
          this.step++;
        })
        .catch((error) => this.addError(error))
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
