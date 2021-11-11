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
      <v-btn icon :color="colors.WarmGrey100">
        <v-icon v-if="arrowState">icon-Sorting-arrow</v-icon>
        <v-icon v-else class="arrow-degree">icon-Sorting-arrow</v-icon>
      </v-btn>

      {{ title }}
    </div>
    <div class="status-wrap">
      <div v-if="errors > 0" class="errors">
        <v-icon color="red">icon-Error</v-icon> {{ errors }}
        {{ $t('keys.globalErrors') }}
      </div>
      <div v-else-if="registered === certificateCount" class="registered">
        <v-icon color="green">icon-Checked</v-icon> {{ $t('keys.noIssues') }}
      </div>
    </div>
  </div>
</template>

<script lang="ts">
// View for a token
import Vue from 'vue';
import { Colors } from '@/global';
import { CertificateStatus, Key, TokenCertificate } from '@/openapi-types';
import { Prop } from 'vue/types/options';

export default Vue.extend({
  props: {
    keys: {
      type: Array as Prop<Key[]>,
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
@import '~styles/tables';
@import '~styles/colors';

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
