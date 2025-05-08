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

  <xrd-button
    v-if="canEnableMaintenanceMode"
    @click="showConfirm=true"
    data-test="enable-maintenance-mode-button"
    outlined
  >
    {{ $t('diagnostics.maintenanceMode.enable') }}

    <xrd-simple-dialog
      v-if="showConfirm"
      title="diagnostics.maintenanceMode.enableTitle"
      data-test="enable-maintenance-mode-dialog"
      save-button-text="action.confirm"
      :scrollable="false"
      :show-close="true"
      :loading="enabling"
      :disable-save="!meta.valid"
      @save="enable"
      @cancel="close"
    >
      <template #text>
        {{ $t('diagnostics.maintenanceMode.enableConfirm') }}
      </template>
      <template #content>
        <v-text-field
          v-model="message"
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
  </xrd-button>


</template>
<script lang="ts" setup>
import { Permissions } from '@/global';
import { computed, ref } from 'vue';
import { XrdButton } from '@niis/shared-ui';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { useSystem } from '@/store/modules/system';
import { useField } from 'vee-validate';
import { i18n } from '@/plugins/i18n';

const emits = defineEmits(['maintenance-mode-enabled']);

const enabling = ref(false);
const showConfirm = ref(false);

const { showError, showSuccess } = useNotifications();
const { hasPermission } = useUser();
const { enableMaintenanceMode } = useSystem();
const { t } = i18n.global;

const canEnableMaintenanceMode = computed(() => hasPermission(Permissions.ENABLE_MAINTENANCE_MODE));

const { meta, errors, value: message, resetField } = useField(
  'message',
  'max:255',
  { initialValue: '' },
);

function close() {
  enabling.value = false;
  showConfirm.value = false;
  resetField()
}

function enable(): void {
  enabling.value = true;
  enableMaintenanceMode(message.value)
    .then(() => {
      showSuccess(t('diagnostics.maintenanceMode.enableSuccess'))
      emits('maintenance-mode-enabled');
    })
    .catch((error) => {
      showError(error);
    })
    .finally(() => close());
}

</script>
<style lang="scss" scoped>

</style>
