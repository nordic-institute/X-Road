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
  <div v-if="canToggleMaintenanceMode" class="d-sm-inline-flex">
    <v-tooltip
      open-delay="500">
      <template v-slot:activator="{ props }">
        <div v-bind="props">
          <v-switch :model-value="enabled"
                    :loading="pending"
                    :disabled="pending"
                    hide-details
                    @update:model-value="showConfirm=true">
            <template #prepend>
              {{ $t('diagnostics.maintenanceMode.label') }}
            </template>
          </v-switch>
        </div>
      </template>
      {{ statusText }}
    </v-tooltip>
    <xrd-confirm-dialog
      v-if="showConfirm && enabled"
      title="diagnostics.maintenanceMode.disableTitle"
      data-test="disable-maintenance-mode-dialog"
      text="diagnostics.maintenanceMode.disableConfirm"
      :loading="updating"
      @cancel="showConfirm=false"
      @accept="changeMode(false)"
    />

    <xrd-simple-dialog
      v-if="showConfirm && !enabled"
      title="diagnostics.maintenanceMode.enableTitle"
      data-test="enable-maintenance-mode-dialog"
      save-button-text="action.confirm"
      :scrollable="false"
      :show-close="true"
      :loading="updating"
      :disable-save="!meta.valid"
      @save="changeMode(true)"
      @cancel="showConfirm=false"
    >
      <template #text>
        {{ $t('diagnostics.maintenanceMode.enableConfirm') }}
      </template>
      <template #content>
        <v-text-field
          v-model="noticeMessage"
          data-test="enable-maintenance-mode-message"
          :label="$t('fields.maintenanceModeMessage')"
          autofocus
          variant="outlined"
          class="dlg-row-input"
          name="securityServerAddress"
          :error-messages="errors"
        />
      </template>
    </xrd-simple-dialog>
  </div>

</template>
<script lang="ts" setup>
import { useSystem } from '@/store/modules/system';
import { computed, ref } from 'vue';
import { useNotifications } from '@/store/modules/notifications';
import { MaintenanceModeStatus } from '@/openapi-types';
import { XrdConfirmDialog } from '@niis/shared-ui';
import { useField } from 'vee-validate';
import { i18n } from '@/plugins/i18n';
import { useUser } from '@/store/modules/user';
import { Permissions } from '@/global';


const showConfirm = ref(false);

const updating = ref(false);
const enabled = ref(false);
const pending = ref(false);
const statusText = ref(undefined as string | undefined);

const { meta, errors, value: noticeMessage, resetField } = useField(
  'message',
  'max:255',
  { initialValue: '' },
);

const { hasPermission } = useUser();
const { fetchMaintenanceModeState, enableMaintenanceMode, disableMaintenanceMode } = useSystem();
const { showError } = useNotifications();
const { t } = i18n.global;

const canToggleMaintenanceMode = computed(() => hasPermission(Permissions.TOGGLE_MAINTENANCE_MODE));

async function changeMode(enable: boolean) {
  updating.value = true;
  if (enable) {
    return enableMaintenanceMode(noticeMessage.value)
      .then(() => update())
      .finally(() => (updating.value = false))
      .finally(() => (showConfirm.value = false))
      .finally(() => resetField());
  } else {
    return disableMaintenanceMode()
      .then(() => update())
      .finally(() => (showConfirm.value = false))
      .finally(() => (updating.value = false))
  }
}

async function update() {
  updating.value = true;
  return fetchMaintenanceModeState()
    .then(mode => {
      statusText.value = t('diagnostics.maintenanceMode.status.' + mode.status);
      switch (mode.status) {
        case MaintenanceModeStatus.PENDING_ENABLE_MAINTENANCE_MODE:
        case MaintenanceModeStatus.ENABLED_MAINTENANCE_MODE:
          pending.value = mode.status === MaintenanceModeStatus.PENDING_ENABLE_MAINTENANCE_MODE;
          enabled.value = true;
          break;
        case MaintenanceModeStatus.PENDING_DISABLE_MAINTENANCE_MODE:
        case MaintenanceModeStatus.DISABLED_MAINTENANCE_MODE:
          pending.value = mode.status === MaintenanceModeStatus.PENDING_DISABLE_MAINTENANCE_MODE;
          enabled.value = false;
          break;
        default:
      }
    })
    .catch((error) => showError(error))
    .finally(() => (updating.value = false))
}

if (canToggleMaintenanceMode.value) {
  update();
}

</script>
<style lang="scss" scoped>

</style>
