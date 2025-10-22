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
  <tr class="body-regular mt-4 mb-4">
    <td class="label text-primary font-weight-medium border-0 pl-0 pr-0">
      {{ $t(label) }}{{ COLON }}
      <div>
        <v-btn
          v-if="canCopy"
          rounded="pill"
          size="x-small"
          prepend-icon="content_copy"
          variant="outlined"
          color="tertiary"
          :loading="copying"
          @click="copyValue"
        >
          {{ $t('action.copy') }}
        </v-btn>
      </div>
    </td>
    <td class="value border-0 pl-4">
      <template v-if="colonize">
        <XrdHashValue wrap-friendly :value="value + ''" />
      </template>
      <template v-else>
        {{ formattedValue }}
      </template>
    </td>
  </tr>
</template>

<script lang="ts" setup>
import { computed, PropType, ref } from 'vue';

import { XrdHashValue } from '../../components';

import { helper } from '../../utils';

const props = defineProps({
  label: {
    type: String,
    required: true,
  },
  value: {
    type: [String, Number],
    default: '',
  },
  values: {
    type: Array as PropType<string[]>,
    default: () => [],
  },
  date: {
    type: Boolean,
    default: false,
  },
  colonize: {
    type: Boolean,
    default: false,
  },
  canCopy: {
    type: Boolean,
    default: false,
  },
});

const COLON = ':';
const copying = ref(false);

const formattedValue = computed(() => {
  if (props.date) {
    return new Date(props.value).toString();
  } else if (props.values.length > 0) {
    return props.values.join(', ');
  }
  return props.value;
});

function copyValue() {
  copying.value = true;
  helper.toClipboard(props.value as string).finally(() => (copying.value = false));
}
</script>

<style lang="scss" scoped>
td.label,
td.value {
  vertical-align: top;
  height: 32px !important;
}

.label {
  width: 160px;
}
</style>
