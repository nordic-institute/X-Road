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
  <template v-if="userStore.isAuthenticated && systemStore.isServerInitialized">
    <v-slide-y-transition group>
      <XrdBanner
        v-for="item in sortedAlerts"
        :key="item.errorCode"
        icon="error"
        color="error"
        translated-text
        :text="$t(item.errorCode, reformatDates(item.metadata))"
      >
        <template #actions>
          <template v-if="!internalCfg">
            <XrdBtn
              v-if="isMissingInternalKey(item)"
              variant="text"
              prepend-icon="key"
              text="keys.addKey"
              @click="navigate(RouteName.InternalConfiguration, 'open-add-key')"
            />
            <XrdBtn
              v-else-if="isInactiveInternalToken(item)"
              variant="text"
              prepend-icon="key"
              text="keys.logIn"
              @click="navigate(RouteName.InternalConfiguration, 'open-login')"
            />
          </template>
          <template v-if="!externalCfg">
            <XrdBtn
              v-if="isMissingExternalKey(item)"
              variant="text"
              prepend-icon="key"
              text="keys.addKey"
              @click="navigate(RouteName.ExternalConfiguration, 'open-add-key')"
            />
            <XrdBtn
              v-else-if="isInactiveExternalToken(item)"
              variant="text"
              prepend-icon="key"
              text="keys.logIn"
              @click="navigate(RouteName.ExternalConfiguration, 'open-login')"
            />
          </template>
        </template>
      </XrdBanner>
    </v-slide-y-transition>
  </template>
</template>

<script lang="ts" setup>
import { computed } from 'vue';

import { useRouter } from 'vue-router';

import { XrdBtn, XrdBanner, formatDateTime } from '@niis/shared-ui';

import { RouteName } from '@/global';
import { useAlerts } from '@/store/modules/alerts';
import { useSystem } from '@/store/modules/system';
import { useUser } from '@/store/modules/user';

type ErrorCoded = { errorCode: string };

const router = useRouter();

const alertsStore = useAlerts();
const userStore = useUser();
const systemStore = useSystem();

const sortedAlerts = computed(() => alertsStore.alerts.toSorted((item1, item2) => item1.errorCode.localeCompare(item2.errorCode)));

const internalCfg = computed(() => router.currentRoute.value.name === RouteName.InternalConfiguration);
const externalCfg = computed(() => router.currentRoute.value.name === RouteName.ExternalConfiguration);

function isMissingInternalKey(item: ErrorCoded) {
  return item.errorCode === 'status.signing_key.internal.missing';
}

function isInactiveInternalToken(item: ErrorCoded) {
  return item.errorCode === 'status.signing_key.internal.token_not_active';
}

function isMissingExternalKey(item: ErrorCoded) {
  return item.errorCode === 'status.signing_key.external.missing';
}

function isInactiveExternalToken(item: ErrorCoded) {
  return item.errorCode === 'status.signing_key.external.token_not_active';
}

function navigate(name: RouteName, action: string) {
  const query = { [action]: null };
  router.push({ name, query });
}

function reformatDates(metadata?: string[]): string[] {
  return (metadata || []).map((item) => {
    if (isNaN(Date.parse(item))) {
      return item;
    }
    return formatDateTime(item, true);
  });
}
</script>
