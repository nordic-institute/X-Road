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
  <v-container class="view-wrap ms-auto">
    <xrd-sub-view-title
      class="pa-4"
      :title="$t('wizard.addSubsystemTitle')"
      :show-close="false"
    />
    <div class="wizard-step-form-content">
      <div class="wizard-info-block">
        <div>
          {{ $t('wizard.subsystem.info1') }}
          <br />
          <br />
          {{ $t('wizard.subsystem.info2') }}
        </div>
        <div class="action-block">
          <xrd-button
            outlined
            data-test="select-subsystem-button"
            @click="showSelectClient = true"
            >{{ $t('wizard.subsystem.selectSubsystem') }}
          </xrd-button>
        </div>
      </div>

      <wizard-row-wrap-t
        label="wizard.memberName"
        tooltip="wizard.client.memberNameTooltip"
      >
        <div data-test="selected-member-name" class="identifier-wrap">
          {{ memberName }}
        </div>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t
        label="wizard.memberClass"
        tooltip="wizard.client.memberClassTooltip"
      >
        <div data-test="selected-member-class" class="identifier-wrap">
          {{ memberClass }}
        </div>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t
        label="wizard.memberCode"
        tooltip="wizard.client.memberCodeTooltip"
      >
        <div data-test="selected-member-code" class="identifier-wrap">
          {{ memberCode }}
        </div>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t
        label="wizard.subsystemCode"
        tooltip="wizard.client.subsystemCodeTooltip"
      >
        <v-text-field
          v-model="subsystemCode"
          v-bind="subsystemCodeAttrs"
          class="wizard-form-input"
          type="text"
          autofocus
          :placeholder="$t('wizard.subsystemCode')"
          variant="outlined"
          data-test="subsystem-code-input"
        ></v-text-field>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t
        v-if="doesSupportSubsystemNames"
        label="wizard.subsystemName"
        tooltip="wizard.client.subsystemNameTooltip"
      >
        <v-text-field
          v-model="subsystemName"
          v-bind="subsystemNameAttrs"
          class="wizard-form-input"
          type="text"
          :placeholder="$t('wizard.subsystemName')"
          variant="outlined"
          data-test="subsystem-name-input"
        ></v-text-field>
      </wizard-row-wrap-t>

      <wizard-row-wrap-t label="wizard.subsystem.registerSubsystem">
        <v-checkbox
          v-model="registerChecked"
          class="register-checkbox"
          data-test="register-subsystem-checkbox"
          density="compact"
        ></v-checkbox>
      </wizard-row-wrap-t>
    </div>

    <div class="button-footer">
      <div class="button-group">
        <xrd-button outlined data-test="cancel-button" @click="exitView"
          >{{ $t('action.cancel') }}
        </xrd-button>
      </div>
      <xrd-button
        :disabled="!meta.valid"
        data-test="submit-add-subsystem-button"
        :loading="submitLoading"
        @click="done"
        >{{ $t('action.addSubsystem') }}
      </xrd-button>
    </div>

    <SelectClientDialog
      :title="$t('wizard.addSubsystemTitle')"
      :search-label="$t('wizard.subsystem.searchLabel')"
      :dialog="showSelectClient"
      :selectable-clients="selectableSubsystems"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />

    <xrd-confirm-dialog
      v-if="confirmRegisterClient"
      title="clients.action.register.confirm.title"
      text="clients.action.register.confirm.text"
      :loading="registerClientLoading"
      @cancel="exitView"
      @accept="registerSubsystem"
    />
  </v-container>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { RouteName } from '@/global';
import { containsClient, createClientId } from '@/util/helpers';
import { Client } from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { useNotifications } from '@/store/modules/notifications';
import { defineRule, useForm } from 'vee-validate';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';
import { useSystem } from '@/store/modules/system';
import WizardRowWrapT from '@/components/ui/WizardRowWrapT.vue';

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
    props: (state) => ({ 'error-messages': state.errors }),
  },
);

const [subsystemName, subsystemNameAttrs] = defineField(
  'addClient.subsystemName',
  {
    props: (state) => ({ 'error-messages': state.errors }),
  },
);

const showSelectClient = ref(false);
const registerChecked = ref(true);
const existingSubsystems = ref([] as Client[]);
const selectableSubsystems = ref([] as Client[]);
const submitLoading = ref(false);
const confirmRegisterClient = ref(false);
const registerClientLoading = ref(false);

const { showError, showSuccess } = useNotifications();
const { doesSupportSubsystemNames } = useSystem();
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

  api
    .post('/clients', body)
    .then(() => {
      submitLoading.value = false;
      showSuccess(t('wizard.subsystem.subsystemAdded'));
      if (registerChecked.value) {
        confirmRegisterClient.value = true;
      } else {
        exitView();
      }
    })
    .catch((error) => {
      submitLoading.value = false;
      showError(error);
    });
});

function registerSubsystem(): void {
  registerClientLoading.value = true;

  const clientId = createClientId(
    props.instanceId,
    props.memberClass,
    props.memberCode,
    subsystemCode.value,
  );
  api
    .put(`/clients/${encodePathParameter(clientId)}/register`, {})
    .then(() => {
      exitView();
      showSuccess(t('wizard.subsystem.subsystemAdded'));
    })
    .catch((error) => {
      exitView();
      showError(error);
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
  api
    .get<Client[]>(
      `/clients?instance=${props.instanceId}&member_class=${props.memberClass}&member_code=${props.memberCode}&show_members=false&exclude_local=true&internal_search=false`,
    )
    .then((res) => (selectableSubsystems.value = res.data))
    .catch((error) => showError(error));

  // Fetch existing subsystems
  api
    .get<Client[]>(
      `/clients?instance=${props.instanceId}&member_class=${props.memberClass}&member_code=${props.memberCode}&internal_search=true`,
    )
    .then((res) => (existingSubsystems.value = res.data))
    .catch((error) => showError(error));
}
</script>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/wizards';
</style>
