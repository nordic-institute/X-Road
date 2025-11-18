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
  <XrdSimpleDialog
    cancel-button-text="action.cancel"
    save-button-text="action.add"
    save-button-icon="add_circle"
    title="trustServices.addCertificationService"
    submittable
    :disable-save="!meta.valid"
    @save="onSave"
    @cancel="$emit('cancel')"
  >
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <XrdCertificateFileUpload v-model:file="certFile" label="trustServices.uploadCertificate" autofocus :readonly="certUploaded" />
        </XrdFormBlockRow>
        <v-expand-transition group>
          <XrdFormBlockRow v-if="!certUploaded" full-length>
            <XrdBtn
              data-test="upload-file-btn"
              class="float-right"
              prepend-icon="upload"
              text="action.upload"
              :disabled="!certFile"
              @click="onUpload"
            />
          </XrdFormBlockRow>
          <template v-else>
            <XrdFormBlockRow full-length>
              <v-checkbox
                v-model="tlsAuthOnly"
                v-bind="tlsAuthOnlyAttrs"
                class="xrd"
                hide-details
                :label="$t('trustServices.addCASettingsCheckbox')"
              />
            </XrdFormBlockRow>
            <XrdFormBlockRow full-length>
              <v-text-field
                v-model="certProfile"
                v-bind="certProfileAttrs"
                data-test="cert-profile-input"
                class="xrd"
                persistent-hint
                :label="$t('trustServices.certProfileInput')"
                :hint="$t('trustServices.certProfileInputExplanation')"
              />
            </XrdFormBlockRow>
            <XrdFormBlockRow full-length>
              <v-select
                v-model="defaultCsrFormat"
                v-bind="defaultCsrFormatAttrs"
                data-test="csr-format-select"
                class="xrd mt-6"
                variant="outlined"
                :label="$t('trustServices.defaultCsrFormat')"
                :items="csrFormatList"
              />
            </XrdFormBlockRow>
            <XrdFormBlockRow full-length>
              <v-checkbox
                v-model="isAcme"
                data-test="acme-checkbox"
                class="xrd"
                hide-details
                :label="$t('trustServices.trustService.settings.acmeCapable')"
              />
            </XrdFormBlockRow>
            <div v-show="isAcme">
              <XrdFormBlockRow full-length>
                <v-text-field
                  v-model="acmeServerDirectoryUrl"
                  v-bind="acmeServerDirectoryUrlAttrs"
                  data-test="acme-server-directory-url-input"
                  class="xrd"
                  persistent-hint
                  :label="$t('fields.acmeServerDirectoryUrl')"
                  :hint="$t('trustServices.acmeServerDirectoryUrlExplanation')"
                />
              </XrdFormBlockRow>
              <XrdFormBlockRow full-length>
                <v-text-field
                  v-model="acmeServerIpAddress"
                  v-bind="acmeServerIpAddressAttrs"
                  data-test="acme-server-ip-address-input"
                  class="xrd"
                  persistent-hint
                  :label="$t('fields.acmeServerIpAddress')"
                  :hint="$t('trustServices.acmeServerIpAddressExplanation')"
                />
              </XrdFormBlockRow>
              <XrdFormBlockRow full-length>
                <v-text-field
                  v-model="authenticationCertificateProfileId"
                  v-bind="authenticationCertificateProfileIdAttrs"
                  data-test="auth-cert-profile-id-input"
                  class="xrd"
                  persistent-hint
                  :label="$t('fields.authenticationCertificateProfileId')"
                  :hint="$t('trustServices.acmeServerAuthProfileIdExplanation')"
                />
              </XrdFormBlockRow>
              <XrdFormBlockRow full-length>
                <v-text-field
                  v-model="signingCertificateProfileId"
                  v-bind="signingCertificateProfileIdAttrs"
                  data-test="sign-cert-profile-id-input"
                  class="xrd"
                  persistent-hint
                  :label="$t('fields.signingCertificateProfileId')"
                  :hint="$t('trustServices.acmeServerSignProfileIdExplanation')"
                />
              </XrdFormBlockRow>
            </div>
          </template>
        </v-expand-transition>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { useForm } from 'vee-validate';
import { useCertificationService } from '@/store/modules/trust-services';
import {
  XrdSimpleDialog,
  useBasicForm,
  useFileRef,
  XrdBtn,
  XrdFormBlock,
  XrdFormBlockRow,
  XrdCertificateFileUpload,
} from '@niis/shared-ui';
import { CsrFormat } from '@/openapi-types';

const emit = defineEmits(['save', 'cancel']);

const commonValidation = { certProfile: 'required', tlsAuthOnly: '' };
const acmeValidation = {
  acmeServerDirectoryUrl: 'required|url',
  acmeServerIpAddress: 'ipAddresses',
};
const isAcme = ref(false);
const validationSchema = computed(() => (isAcme.value ? { ...commonValidation, ...acmeValidation } : commonValidation));

const { meta, defineField, handleSubmit } = useForm({
  validationSchema,
  initialValues: {
    tlsAuthOnly: false,
    certProfile: '',
    defaultCsrFormat: CsrFormat.DER,
    acmeServerDirectoryUrl: '',
    acmeServerIpAddress: '',
    authenticationCertificateProfileId: '',
    signingCertificateProfileId: '',
  },
});
const csrFormatList = Object.values(CsrFormat).map((csrFormat) => ({
  title: csrFormat,
  value: csrFormat,
}));
const [tlsAuthOnly, tlsAuthOnlyAttrs] = defineField('tlsAuthOnly');
const [certProfile, certProfileAttrs] = defineField('certProfile', {
  props: (state) => ({ 'error-messages': state.errors }),
  validateOnModelUpdate: true,
});
const [defaultCsrFormat, defaultCsrFormatAttrs] = defineField('defaultCsrFormat');
const [acmeServerDirectoryUrl, acmeServerDirectoryUrlAttrs] = defineField('acmeServerDirectoryUrl', {
  props: (state) => ({ 'error-messages': state.errors }),
  validateOnModelUpdate: true,
});
const [acmeServerIpAddress, acmeServerIpAddressAttrs] = defineField('acmeServerIpAddress', {
  props: (state) => ({ 'error-messages': state.errors }),
  validateOnModelUpdate: true,
});
const [authenticationCertificateProfileId, authenticationCertificateProfileIdAttrs] = defineField('authenticationCertificateProfileId', {
  props: (state) => ({ 'error-messages': state.errors }),
});
const [signingCertificateProfileId, signingCertificateProfileIdAttrs] = defineField('signingCertificateProfileId', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { loading, addSuccessMessage, addError } = useBasicForm();
const { add } = useCertificationService();

const certUploaded = ref(false);
const certFile = useFileRef();

function onUpload(): void {
  certUploaded.value = true;
}

const onSave = handleSubmit((values) => {
  if (certFile.value) {
    loading.value = true;
    const certService = {
      certificate: certFile.value,
      tls_auth: values.tlsAuthOnly.toString(),
      certificate_profile_info: values.certProfile,
      default_csr_format: values.defaultCsrFormat,
      acme_server_directory_url: values.acmeServerDirectoryUrl,
      acme_server_ip_address: values.acmeServerIpAddress,
      authentication_certificate_profile_id: values.authenticationCertificateProfileId,
      signing_certificate_profile_id: values.signingCertificateProfileId,
    };
    add(certService)
      .then(() => addSuccessMessage('trustServices.certImportedSuccessfully'))
      .then(() => emit('save'))
      .catch((error) => addError(error))
      .finally(() => (loading.value = false));
  }
});
</script>
