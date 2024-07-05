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
<!--
  Member details view
-->
<template>
  <xrd-simple-dialog
    title="securityServers.dialogs.deleteAddress.title"
    data-test="security-server-delete-dialog"
    save-button-text="action.delete"
    submittable
    :scrollable="false"
    :show-close="true"
    :loading="loading"
    :disable-save="!meta.valid"
    @save="deleteSecurityServer"
    @cancel="close"
  >
    <template #text>
      <i18n-t
        scope="global"
        keypath="securityServers.dialogs.deleteAddress.areYouSure"
      >
        <template #securityServer>
          <b>{{ serverCode }}</b>
        </template>
      </i18n-t>
    </template>
    <template #content>
      <v-text-field
        v-model="enteredCode"
        v-bind="enteredCodeAttrs"
        name="serverCode"
        variant="outlined"
        :label="$t('securityServers.dialogs.deleteAddress.enterCode')"
        autofocus
        data-test="verify-server-code"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useSecurityServer } from '@/store/modules/security-servers';
import { useNotifications } from '@/store/modules/notifications';
import { useForm } from 'vee-validate';
import { RouteName } from '@/global';
import { i18n } from '@/plugins/i18n';

/**
 * Component for a Security server details view
 */

const props = defineProps({
  securityServerId: {
    type: String,
    default: '',
  },
  serverCode: {
    type: String,
    default: '',
  },
});

const emits = defineEmits(['cancel']);

const { meta, handleSubmit, defineField } = useForm({
  validationSchema: {
    serverCode: 'required|is:' + props.serverCode,
  },
});
const [enteredCode, enteredCodeAttrs] = defineField('serverCode', {
  props: (state) => ({ 'error-messages': state.errors }),
});
const { delete: deleteSS } = useSecurityServer();
const { showError, showSuccess } = useNotifications();

function close() {
  emits('cancel');
}

const loading = ref(false);
const { t } = i18n.global;
const router = useRouter();
const deleteSecurityServer = handleSubmit(() => {
  loading.value = true;
  deleteSS(props.securityServerId)
    .then(() => {
      showSuccess(t('securityServers.dialogs.deleteAddress.success'), true);
      router.replace({
        name: RouteName.SecurityServers,
      });
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
});
</script>

<style lang="scss" scoped></style>
