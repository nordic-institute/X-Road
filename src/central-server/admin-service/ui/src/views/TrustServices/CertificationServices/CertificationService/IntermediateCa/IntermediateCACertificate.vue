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
  <XrdElevatedViewFixedWidth
    id="intermediate-ca-certificate-details"
    title="cert.certificate"
    close-on-escape
    go-back-on-close
    :breadcrumbs
    :loading
  >
    <XrdCertificate
      v-if="certificateDetails"
      :certificate="certificateDetails"
    />
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts" setup>
import { computed, ref, watchEffect } from 'vue';

import { useI18n } from 'vue-i18n';

import {
  useNotifications,
  XrdCertificate,
  XrdElevatedViewFixedWidth,
} from '@niis/shared-ui';

import { RouteName } from '@/global';
import { CertificateDetails as CertificateDetailsType } from '@/openapi-types';
import {
  useCertificationService,
  useIntermediateCasService,
} from '@/store/modules/trust-services';

const props = defineProps({
  intermediateCaId: {
    type: String,
    required: true,
  },
});

const { t } = useI18n();
const certificateDetails = ref<CertificateDetailsType | undefined>(undefined);
const loading = ref(false);

const { addError } = useNotifications();
const intermediateCasServiceStore = useIntermediateCasService();
const certificationServiceStore = useCertificationService();

const breadcrumbs = computed(() => {
  const crumbs = [
    {
      title: t('tab.main.trustServices'),
      to: { name: RouteName.TrustServices },
    },
  ];

  if (certificationServiceStore.current) {
    crumbs.push({
      title: certificationServiceStore.current?.name || '',
      to: {
        name: RouteName.CertificationServiceDetails,
        params: {
          certificationServiceId: certificationServiceStore.current?.id,
        },
      },
    });

    crumbs.push({
      title: t('trustServices.trustService.pagenavigation.intermediateCas'),
      to: {
        name: RouteName.CertificationServiceIntermediateCas,
        params: {
          certificationServiceId: certificationServiceStore.current?.id,
        },
      },
    });
  }

  if (intermediateCasServiceStore.current) {
    crumbs.push({
      title:
        intermediateCasServiceStore.current?.ca_certificate.subject_common_name,
      to: {
        name: RouteName.IntermediateCaDetails,
        params: {
          intermediateCaId: String(intermediateCasServiceStore.current?.id),
        },
      },
    });
  }

  crumbs.push({
    title: t('cert.certificate'),
  });

  return crumbs;
});

watchEffect(() => {
  const inCaId = Number(props.intermediateCaId);
  certificateDetails.value = undefined;
  loading.value = true;
  intermediateCasServiceStore
    .getIntermediateCa(inCaId)
    .then((resp) => {
      if (resp.data) {
        certificateDetails.value = resp.data.ca_certificate;
        if (resp.data.certification_service_id) {
          return certificationServiceStore.loadById(
            resp.data.certification_service_id,
          );
        }
      }
      return Promise.resolve();
    })
    .catch((err) => addError(err, { navigate: true }))
    .finally(() => (loading.value = false));
});
</script>
