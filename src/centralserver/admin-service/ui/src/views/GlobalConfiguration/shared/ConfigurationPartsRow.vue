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
  <tr>
    <td>
      <v-icon class="internal-conf-icon"> mdi-file-document-outline</v-icon>
      {{ configurationPart.fileName }}
    </td>
    <td>{{ configurationPart.content_identifier }}</td>
    <td>{{ configurationPart.version }}</td>
    <td>{{ configurationPart.file_updated_at | formatDateTime }}</td>
    <td class="td-align-right">
      <xrd-button :outlined="false" text>
        {{ $t('action.download') }}
      </xrd-button>
    </td>
  </tr>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import { ConfigurationPart } from '@/openapi-types';
import { Prop } from 'vue/types/options';

export default Vue.extend({
  props: {
    configurationPart: {
      type: Object as Prop<ConfigurationPart>,
      required: true,
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

.internal-conf-icon {
  margin-right: 15px;
  color: $XRoad-Purple100;
}

.td-align-right {
  text-align: right;
}
</style>
