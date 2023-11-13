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
  <div class="table-title" :class="{ 'table-closed': !arrowState }">
    <div class="title-text" data-test="key-title-sort" @click="arrowClick">
      <v-btn icon variant="text">
        <xrd-icon-base v-if="arrowState">
          <xrd-icon-sorting-arrow />
        </xrd-icon-base>
        <xrd-icon-base v-else class="arrow-degree">
          <xrd-icon-sorting-arrow />
        </xrd-icon-base>
      </v-btn>

      {{ title }}
    </div>
    <div class="status-wrap">
      <div v-if="errors > 0" class="errors">
        <xrd-icon-base color="red">
          <xrd-icon-error />
        </xrd-icon-base>
        {{ errors }}
        {{ $t('keys.globalErrors') }}
      </div>
      <div v-else-if="registered === certificateCount" class="registered">
        <xrd-icon-base color="green">
          <xrd-icon-checked />
        </xrd-icon-base>
        {{ $t('keys.noIssues') }}
      </div>
    </div>
  </div>
</template>

<script lang="ts">
// View for a token
import { defineComponent, PropType } from 'vue';
import { Colors } from '@/global';
import { CertificateStatus, Key, TokenCertificate } from '@/openapi-types';
import { XrdIconChecked, XrdIconError } from '@niis/shared-ui';

export default defineComponent({
  components: { XrdIconError, XrdIconChecked },
  props: {
    keys: {
      type: Array as PropType<Key[]>,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
    arrowState: {
      type: Boolean,
      required: true,
    },
  },
  emits: ['click'],
  data() {
    return {
      errors: 0,
      registered: 0,
      certificateCount: 0,
      colors: Colors,
    };
  },
  computed: {},
  created() {
    this.countStates();
  },
  methods: {
    countStates(): void {
      // Count the amounts of the different Certificate states
      this.keys
        .flatMap((key: Key) => key.certificates)
        .forEach((cert: TokenCertificate) => {
          if (cert.status === CertificateStatus.GLOBAL_ERROR) {
            this.errors++;
          } else if (cert.status === CertificateStatus.REGISTERED) {
            this.registered++;
          }
          this.certificateCount++;
        });
    },
    arrowClick(): void {
      this.$emit('click');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';
@import '@/assets/colors';

.status-wrap {
  text-transform: uppercase;
  font-size: 12px;
  font-weight: 700;
  color: $XRoad-WarmGrey100;
}

.errors {
  color: $XRoad-Error;
}

.table-title {
  margin-top: -1px; // avoid double 2px border with multiple components
  width: 100%;
  border-top: 1px solid $XRoad-WarmGrey30;
  padding: 10px;
  padding-right: 20px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  height: 100%;
  align-items: center;

  .title-text {
    cursor: pointer;
  }
}

.table-closed {
  // Show bottom border only when table is closed
  border-bottom: 1px solid $XRoad-WarmGrey30;
}

.arrow-degree {
  transform: translate(-4px) rotate(-90deg);
}
</style>
