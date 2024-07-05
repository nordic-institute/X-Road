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
  <main>
    <xrd-simple-dialog
      v-if="showUploadCertificateDialog"
      cancel-button-text="action.cancel"
      save-button-text="action.upload"
      title="trustServices.addCertificationService"
      submittable
      :disable-save="!certFile"
      @save="onUpload"
      @cancel="$emit('cancel')"
    >
      <template #content>
        <div class="dlg-input-width">
          <CertificateFileUpload
            v-model:file="certFile"
            label-key="trustServices.uploadCertificate"
            autofocus
          />
        </div>
      </template>
    </xrd-simple-dialog>

    <xrd-simple-dialog
      v-if="showCASettingsDialog"
      cancel-button-text="action.cancel"
      save-button-text="action.save"
      title="trustServices.caSettings"
      submittable
      :disable-save="!meta.valid"
      :loading="loading"
      @save="onSave"
      @cancel="$emit('cancel')"
    >
      <template #content>
        <div class="dlg-input-width">
          <v-checkbox
            v-model="tlsAuthOnly"
            v-bind="tlsAuthOnlyAttrs"
            :label="$t('trustServices.addCASettingsCheckbox')"
          />
          <v-text-field
            v-model="certProfile"
            v-bind="certProfileAttrs"
            variant="outlined"
            :label="$t('trustServices.certProfileInput')"
            :hint="$t('trustServices.certProfileInputExplanation')"
            persistent-hint
            data-test="cert-profile-input"
          />
          <v-checkbox
            v-model="isAcme"
            :label="$t('trustServices.trustService.settings.acmeCapable')"
            hide-details
            class="mt-4"
            data-test="acme-checkbox"
          />
          <v-sheet v-show="isAcme">
            <v-text-field
              v-model="acmeServerDirectoryUrl"
              v-bind="acmeServerDirectoryUrlAttrs"
              :label="$t('fields.acmeServerDirectoryUrl')"
              :hint="$t('trustServices.acmeServerDirectoryUrlExplanation')"
              persistent-hint
              variant="outlined"
              data-test="acme-server-directory-url-input"
              class="py-4"
            />
            <v-text-field
              v-model="acmeServerIpAddress"
              v-bind="acmeServerIpAddressAttrs"
              :label="$t('fields.acmeServerIpAddress')"
              :hint="$t('trustServices.acmeServerIpAddressExplanation')"
              persistent-hint
              variant="outlined"
              data-test="acme-server-ip-address-input"
            />
            <v-text-field
              v-model="authenticationCertificateProfileId"
              v-bind="authenticationCertificateProfileIdAttrs"
              :label="$t('fields.authenticationCertificateProfileId')"
              :hint="$t('trustServices.acmeServerAuthProfileIdExplanation')"
              persistent-hint
              variant="outlined"
              data-test="auth-cert-profile-id-input"
              class="pt-4"
            />
            <v-text-field
              v-model="signingCertificateProfileId"
              v-bind="signingCertificateProfileIdAttrs"
              :label="$t('fields.signingCertificateProfileId')"
              :hint="$t('trustServices.acmeServerSignProfileIdExplanation')"
              persistent-hint
              variant="outlined"
              data-test="sign-cert-profile-id-input"
              class="pt-4"
            />
          </v-sheet>
        </div>
      </template>
    </xrd-simple-dialog>
  </main>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useForm } from 'vee-validate';
import { useBasicForm, useFileRef } from '@/util/composables';
import CertificateFileUpload from '@/components/ui/CertificateFileUpload.vue';
import { useCertificationService } from '@/store/modules/trust-services';

const emit = defineEmits(['save', 'cancel']);

const commonValidation = { certProfile: 'required', tlsAuthOnly: '' };
const acmeValidation = {
  acmeServerDirectoryUrl: 'required|url',
  acmeServerIpAddress: 'ipAddresses',
};
const isAcme = ref(false);
const validationSchema = computed(() =>
  isAcme.value ? { ...commonValidation, ...acmeValidation } : commonValidation,
);

const { meta, defineField, handleSubmit } = useForm({
  validationSchema,
  initialValues: {
    tlsAuthOnly: false,
    certProfile: '',
    acmeServerDirectoryUrl: '',
    acmeServerIpAddress: '',
    authenticationCertificateProfileId: '',
    signingCertificateProfileId: '',
  },
});
const [tlsAuthOnly, tlsAuthOnlyAttrs] = defineField('tlsAuthOnly');
const [certProfile, certProfileAttrs] = defineField('certProfile', {
  props: (state) => ({ 'error-messages': state.errors }),
  validateOnModelUpdate: true,
});
const [acmeServerDirectoryUrl, acmeServerDirectoryUrlAttrs] = defineField(
  'acmeServerDirectoryUrl',
  {
    props: (state) => ({ 'error-messages': state.errors }),
    validateOnModelUpdate: true,
  },
);
const [acmeServerIpAddress, acmeServerIpAddressAttrs] = defineField(
  'acmeServerIpAddress',
  {
    props: (state) => ({ 'error-messages': state.errors }),
    validateOnModelUpdate: true,
  },
);
const [
  authenticationCertificateProfileId,
  authenticationCertificateProfileIdAttrs,
] = defineField('authenticationCertificateProfileId', {
  props: (state) => ({ 'error-messages': state.errors })
});
const [signingCertificateProfileId, signingCertificateProfileIdAttrs] =
  defineField('signingCertificateProfileId', {
    props: (state) => ({ 'error-messages': state.errors })
  });

const { loading, showSuccess, showError, t } = useBasicForm();
const { add } = useCertificationService();

const showCASettingsDialog = ref(false);
const showUploadCertificateDialog = ref(true);
const certFile = useFileRef();

function onUpload(): void {
  showCASettingsDialog.value = true;
  showUploadCertificateDialog.value = false;
}

const onSave = handleSubmit((values) => {
  if (certFile.value) {
    loading.value = true;
    const certService = {
      certificate: certFile.value,
      tls_auth: values.tlsAuthOnly.toString(),
      certificate_profile_info: values.certProfile,
      acme_server_directory_url: values.acmeServerDirectoryUrl,
      acme_server_ip_address: values.acmeServerIpAddress,
      authentication_certificate_profile_id:
        values.authenticationCertificateProfileId,
      signing_certificate_profile_id: values.signingCertificateProfileId,
    };
    add(certService)
      .then(() => showSuccess(t('trustServices.certImportedSuccessfully')))
      .then(() => emit('save'))
      .catch((error) => showError(error))
      .finally(() => (loading.value = false));
  }
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';
</style>
