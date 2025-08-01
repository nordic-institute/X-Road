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
  <xrd-simple-dialog
    title="trustServices.caSettings"
    save-button-text="action.save"
    cancel-button-text="action.cancel"
    submittable
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @cancel="cancelEdit"
    @save="updateCertificationServiceSettings"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-checkbox
          v-model="tlsAuth"
          v-bind="tlsAuthAttrs"
          data-test="tls-auth-checkbox"
          tabindex="0"
          autofocus
          :label="$t('trustServices.addCASettingsCheckbox')"
        />
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { PropType, ref } from 'vue';
import { useForm } from 'vee-validate';
import { useCertificationService } from '@/store/modules/trust-services';
import { ApprovedCertificationService } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { useI18n } from 'vue-i18n';

const props = defineProps({
  certificationService: {
    type: Object as PropType<ApprovedCertificationService>,
    required: true,
  },
});

const emits = defineEmits(['save', 'cancel']);

const { handleSubmit, meta, defineField } = useForm({
  validationSchema: {
    tlsAuth: {},
  },
  initialValues: {
    tlsAuth: props.certificationService.tls_auth,
  },
});

const [tlsAuth, tlsAuthAttrs] = defineField('tlsAuth');

const { showSuccess, showError } = useNotifications();

const { update: updateCertificationService } = useCertificationService();

function cancelEdit() {
  emits('cancel');
}

const loading = ref(false);
const { t } = useI18n();
const updateCertificationServiceSettings = handleSubmit((values) => {
  loading.value = true;
  updateCertificationService(props.certificationService.id, {
    certificate_profile_info:
      props.certificationService.certificate_profile_info,
    tls_auth: values.tlsAuth.toString(),
  })
    .then(() => {
      showSuccess(t('trustServices.trustService.settings.saveSuccess'));
      emits('save');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
});
</script>
