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
    @cancel="$emit('cancel')"
    @save="updateCertificationServiceSettings"
  >
    <template #content>
      <div class="dlg-input-width">
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
            class="py-4"
            data-test="acme-server-directory-url-input"
            variant="outlined"
            autofocus
            persistent-hint
            :label="$t('fields.acmeServerDirectoryUrl')"
            :hint="$t('trustServices.acmeServerDirectoryUrlExplanation')"
          />
          <v-text-field
            v-model="acmeServerIpAddress"
            v-bind="acmeServerIpAddressAttrs"
            variant="outlined"
            :label="$t('fields.acmeServerIpAddress')"
            :hint="$t('trustServices.acmeServerIpAddressExplanation')"
            persistent-hint
            data-test="acme-server-ip-address-input"
          />
        </v-sheet>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { computed, PropType, ref } from 'vue';
import { useCertificationService } from '@/store/modules/trust-services';
import { ApprovedCertificationService } from '@/openapi-types';
import { useForm } from 'vee-validate';
import { useBasicForm } from '@/util/composables';

const props = defineProps({
  certificationService: {
    type: Object as PropType<ApprovedCertificationService>,
    required: true,
  },
});

const emit = defineEmits(['cancel', 'tls-auth-changed']);
const { loading, t, showSuccess, showError } = useBasicForm();
const isAcme = ref(!!props.certificationService.acme_server_directory_url);
const validationSchema = computed(() => {
  return isAcme.value
    ? {
        acmeServerDirectoryUrl: 'required|url',
        acmeServerIpAddress: 'ipAddresses',
      }
    : {};
});

const { meta, defineField, handleSubmit } = useForm({
  validationSchema,
  initialValues: {
    acmeServerDirectoryUrl:
      props.certificationService.acme_server_directory_url,
    acmeServerIpAddress: props.certificationService.acme_server_ip_address,
  },
});

const [acmeServerDirectoryUrl, acmeServerDirectoryUrlAttrs] = defineField(
  'acmeServerDirectoryUrl',
  {
    props: (state) => ({ 'error-messages': state.errors }),
  },
);
const [acmeServerIpAddress, acmeServerIpAddressAttrs] = defineField(
  'acmeServerIpAddress',
  {
    props: (state) => ({ 'error-messages': state.errors }),
  },
);

const { update } = useCertificationService();

const updateCertificationServiceSettings = handleSubmit((values) => {
  loading.value = true;
  update(props.certificationService.id, {
    acme_server_directory_url: isAcme.value
      ? values.acmeServerDirectoryUrl
      : '',
    acme_server_ip_address: isAcme.value ? values.acmeServerIpAddress : '',
  })
    .then(() => {
      showSuccess(t('trustServices.trustService.settings.saveSuccess'));
      emit('tls-auth-changed');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
});
</script>
