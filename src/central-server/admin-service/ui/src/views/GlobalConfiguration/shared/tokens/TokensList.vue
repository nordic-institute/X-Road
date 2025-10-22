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
  <XrdEmptyPlaceholder
    v-if="tokensLoading"
    :data="tokens"
    :loading="tokensLoading"
    :no-items-text="$t('noData.noTokens')"
    skeleton-type="table-heading"
  />

  <TokenExpandable
    v-for="token in tokens"
    v-else
    ref="ref-tokens"
    :key="token.id"
    :token="token"
    :configuration-type="configurationType"
    :loading-keys="loadingKeys"
    @token-login="fetchData"
    @token-logout="fetchData"
    @update-keys="fetchKeys"
  />
</template>

<script lang="ts" setup>
/**
 * Table component for an array of keys
 */
import { PropType, computed, useTemplateRef, onMounted } from 'vue';
import { ConfigurationType } from '@/openapi-types';
import { useToken } from '@/store/modules/tokens';
import TokenExpandable from './TokenExpandable.vue';
import { useRunning, XrdCard } from '@niis/shared-ui';
import { useRoute } from 'vue-router';

type TokenExpandableType = InstanceType<typeof TokenExpandable>;

defineProps({
  configurationType: {
    type: String as PropType<ConfigurationType>,
    required: true,
  },
});

const emit = defineEmits(['update-keys']);

const currentRoute = useRoute();
const refOfTokens = useTemplateRef<TokenExpandableType[]>('ref-tokens');

const { tokensLoading, startTokensLoading, stopTokensLoading } =
    useRunning('tokensLoading'),
  { loadingKeys, startLoadingKeys, stopLoadingKeys } =
    useRunning('loadingKeys');

const tokenStore = useToken();

const tokens = computed(() => tokenStore.getSortedTokens);

async function fetchData() {
  startTokensLoading();
  return tokenStore.fetchTokens().finally(() => stopTokensLoading());
}

function fetchKeys(action: string): void {
  startLoadingKeys();
  tokenStore.fetchTokens().finally(() => stopLoadingKeys());
  emit('update-keys', action);
}

onMounted(() => {
  fetchData().then(() => {
    if (currentRoute.query['open-login'] === null) {
      refOfTokens.value?.find((item) => item.openLogin());
    } else if (currentRoute.query['open-add-key'] === null) {
      refOfTokens.value?.find((item) => item.openAddKey());
    }
  });
});
</script>
