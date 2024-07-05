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
        <v-text-field
          v-model="certProfile"
          v-bind="certProfileAttrs"
          variant="outlined"
          data-test="cert-profile-input"
          persistent-hint
          autofocus
          :label="$t('trustServices.certProfileInput')"
          :hint="$t('trustServices.certProfileInputExplanation')"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { ref, PropType } from 'vue';
import { useForm } from 'vee-validate';
import { useCertificationService } from '@/store/modules/trust-services';
import { ApprovedCertificationService } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { i18n } from '@/plugins/i18n';

const props = defineProps({
  certificationService: {
    type: Object as PropType<ApprovedCertificationService>,
    required: true,
  },
});

const emits = defineEmits(['save', 'cancel']);

const { handleSubmit, meta, defineField } = useForm({
  validationSchema: {
    certProfile: { required: true },
  },
  initialValues: {
    certProfile: props.certificationService.certificate_profile_info,
  },
});

const [certProfile, certProfileAttrs] = defineField('certProfile', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { showSuccess, showError } = useNotifications();

const { update: updateCertificationService } = useCertificationService();

function cancelEdit() {
  emits('cancel');
}

const loading = ref(false);
const { t } = i18n.global;
const updateCertificationServiceSettings = handleSubmit((values) => {
  loading.value = true;
  updateCertificationService(props.certificationService.id, {
    certificate_profile_info: values.certProfile,
    tls_auth: `${props.certificationService.tls_auth}`,
  })
    .then(() => {
      showSuccess(t('trustServices.trustService.settings.saveSuccess'));
      emits('save');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
});
</script>
