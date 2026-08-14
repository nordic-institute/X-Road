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
    title="trustServices.dsTls.settings.title"
    save-button-text="action.save"
    save-button-icon="check"
    cancel-button-text="action.cancel"
    submittable
    :loading="loading"
    :disable-save="isAcme ? !meta.valid || !meta.dirty : !isAcmeMeta.dirty"
    @cancel="$emit('cancel')"
    @save="updateDsTlsCertificationAuthoritySettings"
  >
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-checkbox
            v-model="isAcme"
            data-test="acme-checkbox"
            class="xrd"
            hide-details
            :label="$t('trustServices.trustService.settings.acmeCapable')"
          />
        </XrdFormBlockRow>
        <v-expand-transition>
          <div v-show="isAcme">
            <XrdFormBlockRow full-length>
              <v-text-field
                v-model="acmeServerDirectoryUrl"
                v-bind="acmeServerDirectoryUrlAttrs"
                data-test="acme-server-directory-url-input"
                class="xrd"
                autofocus
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
        </v-expand-transition>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import { computed, PropType } from 'vue';
import { useDsTlsCertificationAuthorityService } from '@/store/modules/trust-services';
import { ApprovedDsTlsCertificationAuthority } from '@/openapi-types';
import { useForm, useField } from 'vee-validate';
import { XrdSimpleDialog, useBasicForm, XrdFormBlock, XrdFormBlockRow } from '@niis/shared-ui';

const props = defineProps({
  dsTlsCertificationAuthority: {
    type: Object as PropType<ApprovedDsTlsCertificationAuthority>,
    required: true,
  },
});

const emit = defineEmits(['cancel', 'save']);
const { loading, addSuccessMessage, addError } = useBasicForm();
const { value: isAcme, meta: isAcmeMeta, resetField } = useField<boolean>('isAcme');
resetField({ value: !!props.dsTlsCertificationAuthority.acme_server_directory_url });
const validationSchema = computed(() => {
  return isAcme.value
    ? {
        acmeServerDirectoryUrl: 'required|url',
      }
    : {};
});

const { meta, defineField, handleSubmit } = useForm({
  validationSchema,
  initialValues: {
    acmeServerDirectoryUrl: props.dsTlsCertificationAuthority.acme_server_directory_url,
    dsTlsCertificateProfileId: props.dsTlsCertificationAuthority.ds_tls_certificate_profile_id,
  },
});

const [acmeServerDirectoryUrl, acmeServerDirectoryUrlAttrs] = defineField('acmeServerDirectoryUrl', {
  props: (state) => ({ 'error-messages': state.errors }),
});
const [dsTlsCertificateProfileId, dsTlsCertificateProfileIdAttrs] = defineField('dsTlsCertificateProfileId', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { update } = useDsTlsCertificationAuthorityService();

const updateDsTlsCertificationAuthoritySettings = handleSubmit((values) => {
  loading.value = true;
  update(props.dsTlsCertificationAuthority.id, {
    acme_server_directory_url: isAcme.value ? values.acmeServerDirectoryUrl : '',
    ds_tls_certificate_profile_id: isAcme.value ? values.dsTlsCertificateProfileId : '',
  })
    .then(() => {
      addSuccessMessage('trustServices.trustService.settings.saveSuccess');
      emit('save');
    })
    .catch((error) => addError(error))
    .finally(() => (loading.value = false));
});
</script>
