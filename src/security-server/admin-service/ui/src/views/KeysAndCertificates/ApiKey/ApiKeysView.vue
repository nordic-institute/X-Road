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
  <XrdApiKeysView
    title="tab.keys.signAndAuthKeys"
    :can-create="canCreate"
    :can-edit="canEdit"
    :can-view="canView"
    :can-revoke="canRevoke"
    :handler="handler"
    :create-api-key-route-name="createViewName"
  >
    <template #append-header>
      <HelpButton
        class="ml-2"
        :help-image="helpImage"
        help-title="keys.helpTitleApi"
        help-text="keys.helpTextApi"
      />
    </template>
    <template #tabs>
      <KeysAndCertificatesTabs />
    </template>
  </XrdApiKeysView>
</template>

<script lang="ts" setup>
import { XrdApiKeysView, ApiKeysHandler, ApiKey } from '@niis/shared-ui';
import { Roles, Permissions, RouteName } from '@/global';
import { useUser } from '@/store/modules/user';
import { useApiKeys } from '@/store/modules/api-keys';
import { computed } from 'vue';
import KeysAndCertificatesTabs from '../KeysAndCertificatesTabs.vue';
import HelpButton from '@/components/ui/HelpButton.vue';
import helpImage from '@/assets/api_keys.png';

const { hasRole, hasPermission } = useUser();
const { fetchApiKeys, deleteApiKey, updateApiKey } = useApiKeys();

const createViewName = RouteName.CreateApiKey;

const canCreate = computed(() => hasPermission(Permissions.CREATE_API_KEY));
const canEdit = computed(() => hasPermission(Permissions.UPDATE_API_KEY));
const canRevoke = computed(() => hasPermission(Permissions.REVOKE_API_KEY));
const canView = computed(() => hasPermission(Permissions.VIEW_API_KEYS));

const handler = computed(
  () =>
    ({
      addApiKey(roles: string[]): Promise<ApiKey> {
        throw new Error('Not needed here.');
      },
      canAssignRole(role: string): boolean {
        return hasRole(role);
      },
      deleteApiKey(apiKeyId: number): Promise<number> {
        return deleteApiKey(apiKeyId).then(() => apiKeyId);
      },
      fetchApiKeys(): Promise<ApiKey[]> {
        return fetchApiKeys();
      },
      updateApiKey(apiKeyId: number, roles: string[]): Promise<ApiKey> {
        return updateApiKey(apiKeyId, roles);
      },
      availableRoles() {
        return Roles;
      },
    }) as ApiKeysHandler,
);
</script>
