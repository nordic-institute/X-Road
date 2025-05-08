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
    v-if="canDisableMaintenanceMode"
    @click="showConfirm=true"
    data-test="disable-maintenance-mode-button"
    outlined
  >

    {{ $t('diagnostics.maintenanceMode.disable') }}

    <xrd-confirm-dialog
      v-if="showConfirm"
      title="diagnostics.maintenanceMode.disableTitle"
      data-test="disable-maintenance-mode-dialog"
      text="diagnostics.maintenanceMode.disableConfirm"
      @cancel="close"
      @accept="disable"
    />
  </xrd-button>


</template>
<script lang="ts" setup>
import { Permissions } from '@/global';
import { computed, ref } from 'vue';
import { XrdButton, XrdConfirmDialog } from '@niis/shared-ui';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { useSystem } from '@/store/modules/system';
import { i18n } from '@/plugins/i18n';

const emits = defineEmits(['maintenance-mode-disabled']);

const disabling = ref(false);
const showConfirm = ref(false);

const { showError, showSuccess } = useNotifications();
const { hasPermission } = useUser();
const { disableMaintenanceMode } = useSystem();
const { t } = i18n.global;

const canDisableMaintenanceMode = computed(() => hasPermission(Permissions.DISABLE_MAINTENANCE_MODE));

function close() {
  disabling.value = false;
  showConfirm.value = false;
}

function disable(): void {
  disabling.value = true;
  disableMaintenanceMode()
    .then(() => {
      showSuccess(t('diagnostics.maintenanceMode.disableSuccess'))
      emits('maintenance-mode-disabled');
    })
    .catch((error) => {
      showError(error);
    })
    .finally(() => close());
}

</script>
<style lang="scss" scoped>

</style>
