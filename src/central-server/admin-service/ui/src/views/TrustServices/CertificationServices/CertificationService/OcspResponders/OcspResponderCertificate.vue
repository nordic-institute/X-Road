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
  Certification Service settings view
-->
<template>
  <XrdElevatedViewFixedWidth
    id="ocsp-responder-certificate-details"
    title="cert.certificate"
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
import { ref, computed, watchEffect } from 'vue';
import {
  useOcspResponderService,
  useCertificationService,
  useIntermediateCasService,
} from '@/store/modules/trust-services';
import {
  XrdCertificate,
  XrdElevatedViewFixedWidth,
  useNotifications,
} from '@niis/shared-ui';
import { OcspResponderCertificateDetails } from '@/openapi-types';
import { RouteName } from '@/global';
import { useI18n } from 'vue-i18n';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';

const props = defineProps({
  ocspResponderId: {
    type: String,
    required: true,
  },
});

type Cert = OcspResponderCertificateDetails | undefined;

const { t } = useI18n();
const { addError } = useNotifications();
const { getOcspResponderCertificate } = useOcspResponderService();
const certificationServiceStore = useCertificationService();
const intermediateCasServiceStore = useIntermediateCasService();

const loading = ref(false);
const certificateDetails = ref<Cert>(undefined);

const breadcrumbs = computed(() => {
  const certificationServiceId = String(
    certificateDetails.value?.certification_service_id,
  );
  const intermediateCaId = certificateDetails.value?.intermediate_ca_id
    ? String(certificateDetails.value?.intermediate_ca_id)
    : undefined;

  const breadcrumbs = [
    {
      title: t('tab.main.trustServices'),
      to: { name: RouteName.TrustServices },
    },
  ] as BreadcrumbItem[];

  if (certificationServiceId) {
    breadcrumbs.push({
      title: certificationServiceStore.current?.name || '',
      to: {
        name: RouteName.CertificationServiceDetails,
        params: {
          certificationServiceId,
        },
      },
    });
    if (intermediateCaId) {
      breadcrumbs.push({
        title: t('trustServices.trustService.pagenavigation.intermediateCas'),
        to: {
          name: RouteName.CertificationServiceIntermediateCas,
          params: {
            certificationServiceId,
          },
        },
      });
    } else {
      breadcrumbs.push({
        title: t('trustServices.trustService.pagenavigation.ocspResponders'),
        to: {
          name: RouteName.CertificationServiceOcspResponders,
          params: {
            certificationServiceId,
          },
        },
      });
    }
  }

  if (intermediateCaId) {
    breadcrumbs.push({
      title:
        intermediateCasServiceStore.current?.ca_certificate
          .subject_common_name || '',
      to: {
        name: RouteName.IntermediateCaDetails,
        params: {
          intermediateCaId,
        },
      },
    });
    breadcrumbs.push({
      title: t('trustServices.trustService.pagenavigation.ocspResponders'),
      to: {
        name: RouteName.IntermediateCaOcspResponders,
        params: {
          intermediateCaId,
        },
      },
    });
  }

  breadcrumbs.push({
    title: t('cert.certificate'),
  });

  return breadcrumbs;
});
watchEffect(() => {
  const ocspResponderId = Number(props.ocspResponderId);
  certificateDetails.value = undefined;
  loading.value = true;
  getOcspResponderCertificate(ocspResponderId)
    .then((resp) => (certificateDetails.value = resp.data))
    .then(() => {
      if (certificateDetails.value?.certification_service_id) {
        return certificationServiceStore.loadById(
          certificateDetails.value.certification_service_id,
        );
      }
      return Promise.resolve();
    })
    .then(() => {
      if (certificateDetails.value?.intermediate_ca_id) {
        return intermediateCasServiceStore.loadById(
          certificateDetails.value.intermediate_ca_id,
        );
      }
      return Promise.resolve();
    })
    .catch((err) => addError(err, { navigate: true }))
    .finally(() => (loading.value = false));
});
</script>
