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
  <XrdBtn
    v-if="canDownload"
    :loading="downloading"
    data-test="download-diagnostics-report-button"
    variant="outlined"
    prepend-icon="download"
    text="diagnostics.downloadReport"
    @click="download"
  />
</template>
<script lang="ts" setup>
import { Permissions } from '@/global';
import { computed, ref } from 'vue';
import { XrdBtn, useNotifications } from '@niis/shared-ui';
import * as api from '@/util/api';
import { helper } from '@niis/shared-ui';
import { useUser } from '@/store/modules/user';

const { addError } = useNotifications();
const { hasPermission } = useUser();

const downloading = ref(false);

const canDownload = computed(() =>
  hasPermission(Permissions.DOWNLOAD_DIAGNOSTICS_REPORT),
);

function download(): void {
  downloading.value = true;
  api
    .get('/diagnostics/info/download', { responseType: 'blob' })
    .then((res) => helper.saveResponseAsFile(res, 'diagnostic-report.json'))
    .catch((error) => addError(error))
    .finally(() => (downloading.value = false));
}
</script>
<style lang="scss" scoped></style>
