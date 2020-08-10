<template>
  <div>
    <certificate-line childKey="version" :sourceObject="certificate" />
    <certificate-line childKey="serial" :sourceObject="certificate" />
    <certificate-line
      childKey="signature_algorithm"
      :sourceObject="certificate"
    />
    <certificate-line
      childKey="issuer_distinguished_name"
      :sourceObject="certificate"
    />
    <certificate-line childKey="not_before" :sourceObject="certificate" date />
    <certificate-line childKey="not_after" :sourceObject="certificate" date />
    <certificate-line
      childKey="subject_distinguished_name"
      :sourceObject="certificate"
    />

    <certificate-line
      childKey="public_key_algorithm"
      :sourceObject="certificate"
    />
    <certificate-line
      childKey="rsa_public_key_modulus"
      :label="$t('cert.rsaModulus')"
      :sourceObject="certificate"
      chunk
    />

    <certificate-line
      childKey="rsa_public_key_exponent"
      :label="$t('cert.rsaExp')"
      :sourceObject="certificate"
    />

    <certificate-line childKey="state" :sourceObject="certificate" />
    <certificate-line
      childKey="key_usages"
      arrayType
      :sourceObject="certificate"
    />
    <certificate-line childKey="signature" :sourceObject="certificate" chunk />
  </div>
</template>

<script lang="ts">
/**
 * Component for displaying detailed certificate information
 */

import Vue from 'vue';
import CertificateLine from './CertificateLine.vue';

export default Vue.extend({
  components: {
    CertificateLine,
  },
  props: {
    certificate: {
      type: Object,
      required: true,
    },
  },
});
</script>

<style lang="scss" scoped></style>
