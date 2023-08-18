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
    <div
      v-if="certificateDetails"
      class="certificate-details-wrapper xrd-default-shadow"
    >
      <xrd-sub-view-title :title="$t('cert.certificate')" @close="close" />
      <div class="pl-4">
        <section>
          <div class="dtlv-cert-hash mt-8">
            <certificateHash :hash="certificateDetails.hash" />
          </div>
          <div class="mt-6">
            <certificateInfo :certificate="certificateDetails" />
          </div>
        </section>
      </div>
    </div>
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import CertificateHash from '@/components/certificate/CertificateHash.vue';
import CertificateInfo from '@/components/certificate/CertificateInfo.vue';
import { CertificateDetails } from '@/openapi-types';

export default defineComponent({
  name: 'CertificateDetails',
  components: {
    CertificateInfo,
    CertificateHash,
  },
  props: {
    certificateDetails: {
      type: Object as () => CertificateDetails,
      required: true,
    },
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';

.certificate-details-wrapper {
  display: flex;
  justify-content: center;
  flex-direction: column;
  font-size: $XRoad-DefaultFontSize;
  max-width: 850px;
  height: 100%;
  width: 100%;
  background-color: $XRoad-White100;
  padding: 16px;
  margin: auto;
  border-radius: 4px;
}
</style>
