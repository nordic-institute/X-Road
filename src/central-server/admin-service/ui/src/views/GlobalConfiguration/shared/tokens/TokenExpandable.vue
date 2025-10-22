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
  <XrdExpandable
    class="expandable"
    :data-test="`token-${token.name}-expandable`"
    :is-open="isExpanded(token.id)"
    :header-classes="tokenStatusClass"
    @open="toggleToken"
  >
    <template #link="{ toggle, opened }">
      <div
        data-test="token-name"
        class="font-weight-medium"
        :class="{ 'on-surface': opened, 'on-surface-variant': !opened }"
        @click="toggle"
      >
        {{ $t('keys.token.label') }} {{ token.name }}
      </div>
    </template>

    <template #action>
      <div class="action-slot-wrapper">
        <TokenLoginButton
          ref="tokenLoginButton"
          class="token-logging-button"
          :token="token"
          @token-logout="$emit('token-logout')"
          @token-login="$emit('token-login')"
        />
      </div>
    </template>

    <template #content>
      <div class="pl-4 pr-4 pb-4">
        <div class="d-flex flex-row pl-4 pt-4 pb-4">
          <div class="font-weight-medium">
            {{ $t('tokens.keysInfoMessage') }}
          </div>
          <v-spacer />
          <XrdBtn
            v-if="showAddKey"
            data-test="token-add-key-button"
            variant="outlined"
            prepend-icon="add_circle"
            text="keys.addKey"
            :disabled="!canAddKey"
            @click="showAddKeyDialog = true"
          />
        </div>

        <!-- SIGN keys table -->
        <div v-if="token.configuration_signing_keys">
          <KeysTable
            :keys="signingKeys"
            :loading-keys="loadingKeys"
            @update-keys="$emit('update-keys', $event)"
          />
        </div>
      </div>
      <SigningKeyAddDialog
        v-if="showAddKeyDialog"
        :configuration-type="configurationType"
        :token-id="token.id"
        @cancel="showAddKeyDialog = false"
        @key-add="addKey"
      />
    </template>
  </XrdExpandable>
</template>

<script lang="ts" setup>
import { PropType, ref, computed, useTemplateRef } from 'vue';
import { Permissions } from '@/global';
import { useToken } from '@/store/modules/tokens';
import { useUser } from '@/store/modules/user';
import { ConfigurationType, PossibleTokenAction, Token } from '@/openapi-types';
import KeysTable from './KeysTable.vue';
import TokenLoginButton from './TokenLoginButton.vue';
import SigningKeyAddDialog from './dialogs/SigningKeyAddDialog.vue';
import { XrdExpandable, XrdBtn } from '@niis/shared-ui';

type LoginButtonType = InstanceType<typeof TokenLoginButton>;

const props = defineProps({
  token: {
    type: Object as PropType<Token>,
    required: true,
  },
  configurationType: {
    type: String as PropType<ConfigurationType>,
    required: true,
  },
  loadingKeys: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['update-keys', 'token-login', 'token-logout']);

defineExpose({
  openAddKey(): boolean {
    if (showAddKey.value && canAddKey.value) {
      setTokenExpanded(props.token.id);
      showAddKeyDialog.value = true;
      return true;
    }
    return false;
  },
  openLogin(): boolean {
    return tokenLoginButton.value?.openLogin() || false;
  },
});
const tokenLoginButton = useTemplateRef<LoginButtonType>('tokenLoginButton');
const showAddKeyDialog = ref(false);

const { hasPermission } = useUser();
const {
  tokenExpanded: isExpanded,
  setTokenHidden,
  setTokenExpanded,
} = useToken();

const showAddKey = computed(() =>
  hasPermission(Permissions.GENERATE_SIGNING_KEY),
);
const canAddKey = computed(
  () =>
    props.token.possible_actions?.includes(
      ConfigurationType.INTERNAL == props.configurationType
        ? PossibleTokenAction.GENERATE_INTERNAL_KEY
        : PossibleTokenAction.GENERATE_EXTERNAL_KEY,
    ) || false,
);

const tokenStatusClass = computed(() =>
  props.token.logged_in
    ? ['logged-out', 'on-surface-variant']
    : ['logged-in', 'on-surface'],
);
const tokenStatusColor = computed(() =>
  props.token.logged_in ? 'logged-out' : 'logged-in',
);
const signingKeys = computed(
  () =>
    props.token.configuration_signing_keys?.filter(
      (key) => key.source_type === props.configurationType,
    ) || [],
);

function addKey(): void {
  showAddKeyDialog.value = false;
  emit('update-keys', 'add');
}

function toggleToken(opened: boolean): void {
  if (opened) {
    setTokenExpanded(props.token.id);
  } else {
    setTokenHidden(props.token.id);
  }
}
</script>
