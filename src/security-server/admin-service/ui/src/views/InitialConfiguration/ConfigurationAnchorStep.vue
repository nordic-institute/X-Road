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
  <XrdWizardStep>
    <XrdFormBlock class="body-regular">
      <v-row align="center" no-gutters>
        <v-col cols="auto">{{ $t('initialConfiguration.anchor.info') }}</v-col>
        <v-col>
          <UploadConfigurationAnchorDialog init-mode @uploaded="onAnchorUploaded" />
        </v-col>
      </v-row>
    </XrdFormBlock>
    <v-slide-y-transition>
      <XrdFormBlock v-if="configurationAnchor" class="mt-6 body-regular">
        <v-row align="center" no-gutters>
          <v-col class="font-weight-bold" cols="auto" sm="3">
            {{ $t('initialConfiguration.anchor.hash') }}
          </v-col>
          <v-col sm="9">
            <XrdHashValue :value="configurationAnchor.hash" wrap-friendly />
          </v-col>
        </v-row>
        <v-row class="mt-4" align="center" no-gutters>
          <v-col class="font-weight-bold" cols="auto" sm="3">
            {{ $t('initialConfiguration.anchor.generated') }}
          </v-col>
          <v-col sm="9">
            <XrdDateTime :value="configurationAnchor.created_at" />
          </v-col>
        </v-row>
      </XrdFormBlock>
    </v-slide-y-transition>
    <template #footer>
      <v-spacer />
      <XrdBtn
        :disabled="!configurationAnchor"
        data-test="configuration-anchor-save-button"
        :text="saveButtonText"
        variant="flat"
        @click="done"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts" setup>
import { ref } from 'vue';

import { useNotifications, XrdBtn, XrdDateTime, XrdHashValue, XrdWizardStep, XrdFormBlock } from '@niis/shared-ui';

import { Anchor } from '@/openapi-types';
import { useGeneral } from '@/store/modules/general';
import { useSystem } from '@/store/modules/system';
import { useInitializationV2 } from '@/store/modules/initializationV2';

import UploadConfigurationAnchorDialog from '@/views/Settings/SystemParameters/UploadConfigurationAnchorDialog.vue';

withDefaults(
  defineProps<{
    saveButtonText?: string;
  }>(),
  {
    saveButtonText: 'action.continue',
  },
);

const emit = defineEmits<{ done: [] }>();

const { addError } = useNotifications();
const { fetchConfigurationAnchor: apiFetchConfigurationAnchor } = useSystem();
const { fetchMemberClassesForCurrentInstance } = useGeneral();

const configurationAnchor = ref<Anchor | undefined>();

function onAnchorUploaded() {
  apiFetchConfigurationAnchor()
    .then((data) => (configurationAnchor.value = data))
    .catch((error) => addError(error));

  // Refresh v2 initialization status so anchorImported updates
  useInitializationV2().fetchStatus();

  // Fetch member classes for owner member step after anchor is ready
  fetchMemberClassesForCurrentInstance();
}

function done(): void {
  emit('done');
}
</script>

<style lang="scss" scoped></style>
