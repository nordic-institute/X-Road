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
  <XrdElevatedViewSimple title="wizard.addSubsystemTitle">
    <XrdWizardStep
      title="wizard.subsystem.info1"
      sub-title="wizard.subsystem.info2"
    >
      <template #append-header>
        <XrdBtn
          variant="outlined"
          data-test="select-subsystem-button"
          text="wizard.subsystem.selectSubsystem"
          @click="showSelectClient = true"
        />
      </template>
      <XrdFormBlock class="mb-4">
        <XrdFormBlockRow description="wizard.client.memberNameTooltip">
          <v-text-field
            data-test="selected-member-name"
            class="xrd"
            readonly
            hide-details
            :model-value="memberName"
            :label="$t('wizard.memberName')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow description="wizard.client.memberClassTooltip">
          <v-text-field
            data-test="selected-member-class"
            class="xrd"
            readonly
            hide-details
            :model-value="memberClass"
            :label="$t('wizard.memberClass')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow description="wizard.client.memberCodeTooltip">
          <v-text-field
            data-test="selected-member-code"
            class="xrd"
            hide-details
            readonly
            :model-value="memberCode"
            :label="$t('wizard.memberCode')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow
          description="wizard.client.subsystemCodeTooltip"
          adjust-against-content
        >
          <v-text-field
            v-model="subsystemCode"
            v-bind="subsystemCodeAttrs"
            data-test="subsystem-code-input"
            class="xrd"
            autofocus
            :label="$t('wizard.subsystemCode')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow
          v-if="doesSupportSubsystemNames"
          description="wizard.client.subsystemNameTooltip"
          adjust-against-content
        >
          <v-text-field
            v-model="subsystemName"
            v-bind="subsystemNameAttrs"
            data-test="subsystem-name-input"
            class="xrd"
            :label="$t('wizard.subsystemName')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow>
          <v-checkbox
            v-model="registerChecked"
            data-test="register-subsystem-checkbox"
            class="xrd"
            density="compact"
            hide-details
            :label="$t('wizard.subsystem.registerSubsystem')"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
      <template #footer>
        <XrdBtn
          data-test="cancel-button"
          variant="text"
          text="action.cancel"
          @click="exitView"
        />
        <v-spacer />
        <XrdBtn
          data-test="submit-add-subsystem-button"
          text="action.addSubsystem"
          :disabled="!meta.valid"
          :loading="submitLoading"
          @click="done"
        />

        <SelectClientDialog
          v-if="showSelectClient"
          title="wizard.addSubsystemTitle"
          :search-label="$t('wizard.subsystem.searchLabel')"
          :selectable-clients="selectableSubsystems"
          @cancel="showSelectClient = false"
          @save="saveSelectedClient"
        />

        <XrdConfirmDialog
          v-if="confirmRegisterClient"
          title="clients.action.register.confirm.title"
          text="clients.action.register.confirm.text"
          :loading="registerClientLoading"
          @cancel="exitView"
          @accept="registerSubsystem"
        />
      </template>
    </XrdWizardStep>
  </XrdElevatedViewSimple>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { RouteName } from '@/global';
import { containsClient, createClientId } from '@/util/helpers';
import { Client } from '@/openapi-types';
import { defineRule, useForm } from 'vee-validate';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { useSystem } from '@/store/modules/system';

import {
  useNotifications,
  XrdElevatedViewSimple,
  XrdBtn,
  XrdFormBlock,
  XrdFormBlockRow,
  helper,
  XrdWizardStep,
} from '@niis/shared-ui';
import { useClients } from '@/store/modules/clients';
import { useClient } from '@/store/modules/client';

const props = defineProps({
  instanceId: {
    type: String,
    required: true,
  },
  memberClass: {
    type: String,
    required: true,
  },
  memberCode: {
    type: String,
    required: true,
  },
  memberName: {
    type: String,
    required: true,
  },
});

const { t } = useI18n();

function uniqueClient(subsystemCode: string) {
  if (!subsystemCode) {
    return true;
  }

  if (
    containsClient(
      existingSubsystems.value,
      props.memberClass,
      props.memberCode,
      subsystemCode,
    )
  ) {
    return t('wizard.subsystem.subsystemExists');
  }

  return true;
}

defineRule('uniqueClient', uniqueClient);

const { meta, handleSubmit, defineField, setFieldValue } = useForm({
  validationSchema: {
    'addClient.subsystemCode': 'required|xrdIdentifier|max:255|uniqueClient',
    'addClient.subsystemName': 'max:255',
  },
  initialValues: {
    'addClient.subsystemCode': '',
    'addClient.subsystemName': '',
  },
});

const [subsystemCode, subsystemCodeAttrs] = defineField(
  'addClient.subsystemCode',
  {
    props: helper.veePropMapper,
  },
);

const [subsystemName, subsystemNameAttrs] = defineField(
  'addClient.subsystemName',
  {
    props: helper.veePropMapper,
  },
);

const showSelectClient = ref(false);
const registerChecked = ref(true);
const existingSubsystems = ref([] as Client[]);
const selectableSubsystems = ref([] as Client[]);
const submitLoading = ref(false);
const confirmRegisterClient = ref(false);
const registerClientLoading = ref(false);

const { addError, addSuccessMessage } = useNotifications();
const { doesSupportSubsystemNames } = useSystem();
const { addClient, searchClients } = useClients();
const { registerClient } = useClient();
const router = useRouter();

fetchData();

const done = handleSubmit((values) => {
  submitLoading.value = true;

  const body = {
    client: {
      member_name: props.memberName,
      member_class: props.memberClass,
      member_code: props.memberCode,
      subsystem_code: values.addClient.subsystemCode,
      subsystem_name: values.addClient.subsystemName,
    },
    ignore_warnings: false,
  };

  addClient(body)
    .then(() => {
      addSuccessMessage('wizard.subsystem.subsystemAdded', {}, true);
      if (registerChecked.value) {
        confirmRegisterClient.value = true;
      } else {
        exitView();
      }
    })
    .catch((error) => addError(error))
    .finally(() => (submitLoading.value = false));
});

function registerSubsystem(): void {
  registerClientLoading.value = true;

  const clientId = createClientId(
    props.instanceId,
    props.memberClass,
    props.memberCode,
    subsystemCode.value,
  );
  registerClient(clientId)
    .then(() => {
      addSuccessMessage('wizard.subsystem.subsystemAdded', {}, true);
      exitView();
    })
    .catch((error) => {
      addError(error, { preserve: true });
      exitView();
    });
}

function exitView(): void {
  registerClientLoading.value = false;
  confirmRegisterClient.value = false;
  submitLoading.value = false;
  router.replace({ name: RouteName.Clients });
}

function saveSelectedClient(selectedMember: Client): void {
  setFieldValue('addClient.subsystemCode', selectedMember.subsystem_code || '');
  setFieldValue('addClient.subsystemName', selectedMember.subsystem_name || '');

  showSelectClient.value = false;
}

function fetchData(): void {
  // Fetch selectable subsystems
  searchClients({
    instance: props.instanceId,
    member_class: props.memberClass,
    member_code: props.memberCode,
    show_members: false,
    exclude_local: true,
    internal_search: false,
  })
    .then((data) => (selectableSubsystems.value = data))
    .catch((error) => addError(error));

  // Fetch existing subsystems
  searchClients({
    instance: props.instanceId,
    member_class: props.memberClass,
    member_code: props.memberCode,
    internal_search: true,
  })
    .then((data) => (existingSubsystems.value = data))
    .catch((error) => addError(error));
}
</script>

<style lang="scss" scoped></style>
