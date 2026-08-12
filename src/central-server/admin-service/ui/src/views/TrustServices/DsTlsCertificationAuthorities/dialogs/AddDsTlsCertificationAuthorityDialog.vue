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
    title="trustServices.dsTls.addDsTlsCertificationAuthority"
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
              <v-text-field
                v-model="name"
                v-bind="nameAttrs"
                data-test="ds-tls-ca-name-input"
                class="xrd"
                persistent-hint
                :label="$t('fields.name')"
                :hint="$t('trustServices.dsTls.nameExplanation')"
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
                  v-model="dsTlsCertificateProfileId"
                  v-bind="dsTlsCertificateProfileIdAttrs"
                  data-test="ds-tls-cert-profile-id-input"
                  class="xrd"
                  persistent-hint
                  :label="$t('fields.dsTlsCertificateProfileId')"
                  :hint="$t('trustServices.dsTls.dsTlsCertificateProfileIdExplanation')"
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
import { useDsTlsCertificationAuthorityService } from '@/store/modules/trust-services';
import {
  XrdSimpleDialog,
  useBasicForm,
  useFileRef,
  XrdBtn,
  XrdFormBlock,
  XrdFormBlockRow,
  XrdCertificateFileUpload,
} from '@niis/shared-ui';

const emit = defineEmits(['save', 'cancel']);

const commonValidation = { name: 'required' };
const acmeValidation = { acmeServerDirectoryUrl: 'required|url' };
const isAcme = ref(false);
const validationSchema = computed(() => (isAcme.value ? { ...commonValidation, ...acmeValidation } : commonValidation));

const { meta, defineField, handleSubmit } = useForm({
  validationSchema,
  initialValues: {
    name: '',
    acmeServerDirectoryUrl: '',
    dsTlsCertificateProfileId: '',
  },
});

const [name, nameAttrs] = defineField('name', {
  props: (state) => ({ 'error-messages': state.errors }),
  validateOnModelUpdate: true,
});
const [acmeServerDirectoryUrl, acmeServerDirectoryUrlAttrs] = defineField('acmeServerDirectoryUrl', {
  props: (state) => ({ 'error-messages': state.errors }),
  validateOnModelUpdate: true,
});
const [dsTlsCertificateProfileId, dsTlsCertificateProfileIdAttrs] = defineField('dsTlsCertificateProfileId', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { loading, addSuccessMessage, addError } = useBasicForm();
const { add } = useDsTlsCertificationAuthorityService();

const certUploaded = ref(false);
const certFile = useFileRef();

function onUpload(): void {
  certUploaded.value = true;
}

const onSave = handleSubmit((values) => {
  if (certFile.value) {
    loading.value = true;
    const newCa = {
      certificate: certFile.value,
      name: values.name,
      acme_server_directory_url: isAcme.value ? values.acmeServerDirectoryUrl : '',
      ds_tls_certificate_profile_id: isAcme.value ? values.dsTlsCertificateProfileId : '',
    };
    add(newCa)
      .then(() => addSuccessMessage('trustServices.certImportedSuccessfully'))
      .then(() => emit('save'))
      .catch((error) => addError(error))
      .finally(() => (loading.value = false));
  }
});
</script>
