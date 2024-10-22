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
  <v-card variant="flat" class="xrd-card diagnostic-card">
    <v-card-title class="text-h5" data-test="diagnostics-java-version">
      {{ $t('diagnostics.javaVersion.title') }}
    </v-card-title>

    <v-card-text class="xrd-card-text">
      <table class="xrd-table">
        <thead>
          <tr>
            <th class="status-column">{{ $t('diagnostics.status') }}</th>
            <th>{{ $t('diagnostics.message') }}</th>
            <th class="level-column">
              {{ $t('diagnostics.javaVersion.vendor') }}
            </th>
            <th class="level-column">
              {{ $t('diagnostics.javaVersion.title') }}
            </th>
            <th class="level-column">
              {{ $t('diagnostics.javaVersion.earliest') }}
            </th>
            <th class="level-column">
              {{ $t('diagnostics.javaVersion.latest') }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td data-test="java-icon">
              <xrd-status-icon
                v-if="securityServerVersion.using_supported_java_version"
                status="ok"
              />
              <xrd-status-icon v-else status="error" />
            </td>
            <td
              v-if="securityServerVersion.using_supported_java_version"
              data-test="java-message"
            >
              {{ $t('diagnostics.javaVersion.ok') }}
            </td>
            <td v-else data-test="java-message">
              {{ $t('diagnostics.javaVersion.notSupported') }}
            </td>
            <td data-test="java-vendor">
              {{ securityServerVersion.java_vendor }}
            </td>
            <td data-test="java-version">
              {{ securityServerVersion.java_version }}
            </td>
            <td data-test="java-min">
              {{ securityServerVersion.min_java_version }}
            </td>
            <td data-test="java-max">
              {{ securityServerVersion.max_java_version }}
            </td>
          </tr>
        </tbody>
      </table>
    </v-card-text>
  </v-card>
</template>
<script lang="ts">
import { mapState } from 'pinia';
import { useSystem } from '@/store/modules/system';
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'DiagnosticsJavaVersionCard',
  computed: {
    ...mapState(useSystem, ['securityServerVersion']),
  },
});
</script>
<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

h3 {
  color: $XRoad-Black100;
  font-size: 24px;
  font-weight: 400;
  letter-spacing: normal;
  line-height: 2rem;
}

.xrd-card-text {
  padding-left: 0;
  padding-right: 0;
}

.diagnostic-card {
  width: 100%;
  margin-bottom: 30px;

  &:first-of-type {
    margin-top: 40px;
  }
}

.status-column {
  width: 80px;
}

.level-column {
  @media only screen and (min-width: 1200px) {
    width: 20%;
  }
}
</style>
