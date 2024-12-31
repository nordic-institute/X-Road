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

  <xrd-button
    v-if="canDownload"
    :loading="downloading"
    @click="download"
    data-test="system-info-download-button"
    outlined
  >
    <xrd-icon-base class="xrd-large-button-icon">
      <xrd-icon-download />
    </xrd-icon-base>
    {{ $t('action.download') }}
  </xrd-button>

</template>
<script lang="ts" setup>
import { Permissions } from '@/global';
import { computed, ref } from 'vue';
import { XrdButton, XrdIconDownload } from '@niis/shared-ui';
import * as api from '@/util/api';
import { saveResponseAsFile } from '@/util/helpers';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';

const { showError } = useNotifications();
const { hasPermission } = useUser();

const downloading = ref(false);

const canDownload = computed(() => hasPermission(Permissions.DOWNLOAD_SYSTEM_INFO));

function download(): void {
  downloading.value = true;
  api.get('/diagnostics/info/download', { responseType: 'blob' })
    .then((res) => saveResponseAsFile(res, 'system-information.json'))
    .catch((error) => {
      showError(error);
    })
    .finally(() => (downloading.value = false));
}

</script>
<style lang="scss" scoped>
@use '@/assets/colors';
@use '@/assets/tables';

h3 {
  color: colors.$Black100;
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
