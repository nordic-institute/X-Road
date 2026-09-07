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
  DS TLS certification authority settings view. Simplified compared to the certification-services settings:
  no OCSP responders, no per-usage-type ACME profile ids - only the ACME directory URL and a single DS TLS
  certificate profile id.
-->
<template>
  <XrdSubView id="ds-tls-certification-authority-settings">
    <XrdCard data-test="ds-tls-acme-card" :loading>
      <XrdCardTable>
        <v-table class="xrd">
          <thead>
            <tr>
              <th>
                {{ $t('trustServices.trustService.settings.acmeCapable') }}
              </th>
              <th>
                {{ $t('fields.acmeServerDirectoryUrl') }}
              </th>
              <th>
                {{ $t('fields.dsTlsCertificateProfileId') }}
              </th>
              <th v-if="allowEditSettings" class="pa-0"></th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td data-test="is-acme-capable">
                {{ current?.acme_server_directory_url ? $t('common.true') : $t('common.false') }}
              </td>
              <td data-test="acme-server-directory-url">
                {{ current?.acme_server_directory_url || '-' }}
              </td>
              <td data-test="ds-tls-certificate-profile-id">
                {{ current?.ds_tls_certificate_profile_id || '-' }}
              </td>
              <td v-if="allowEditSettings" class="pl-2 pr-2">
                <XrdBtn
                  data-test="edit-ds-tls-acme-btn"
                  class="float-right"
                  variant="text"
                  color="tertiary"
                  text="action.edit"
                  @click="showEditAcmeServerDialog = true"
                />
              </td>
            </tr>
          </tbody>
        </v-table>
      </XrdCardTable>
    </XrdCard>

    <EditAcmeServerDialog
      v-if="showEditAcmeServerDialog && current"
      :ds-tls-certification-authority="current"
      @cancel="hideEditAcmeServerDialog"
      @save="hideEditAcmeServerDialog"
    />
  </XrdSubView>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue';
import { useDsTlsCertificationAuthorityService } from '@/store/modules/trust-services';
import { Permissions } from '@/global';
import { useUser } from '@/store/modules/user';
import EditAcmeServerDialog from './dialogs/EditAcmeServerDialog.vue';
import { XrdSubView, XrdCardTable, XrdCard, XrdBtn } from '@niis/shared-ui';

const { hasPermission } = useUser();
const dsTlsCertificationAuthorityStore = useDsTlsCertificationAuthorityService();

const showEditAcmeServerDialog = ref(false);

const current = computed(() => dsTlsCertificationAuthorityStore.current);
const loading = computed(() => dsTlsCertificationAuthorityStore.loadingCurrent);
const allowEditSettings = computed(() => hasPermission(Permissions.EDIT_APPROVED_DS_TLS_CA));

function hideEditAcmeServerDialog() {
  showEditAcmeServerDialog.value = false;
}
</script>
