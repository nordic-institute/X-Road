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
  <v-container class="pa-0" fluid>
    <v-slide-y-transition group :hide-on-leave="hideOnLeave">
      <template v-for="notification in errors" :key="notification.id">
        <v-banner
          data-test="contextual-alert"
          class="alert border border-s-xl"
          bg-color="background"
          rounded
          :icon="color(notification.asWarning)"
          :color="color(notification.asWarning)"
          :border="color(notification.asWarning)"
        >
          <v-banner-text>
            <p v-if="notification.message">
              {{ notification.message }}
            </p>
            <p v-for="meta in groupMetaData(notification.error.metaData)" :key="meta.key">
              {{ meta.translatable ? $t(meta.key, meta.args) : meta.key }}
            </p>
            <p v-if="notification.error.correlationId">
              {{ $t('alert.id') + ':' }}
              {{ notification.error.correlationId }}
            </p>
            <p v-if="notification.count > 1 && !notification.asWarning">
              {{ $t('alert.count') }}
              {{ notification.count }}
            </p>

            <ul v-if="notification.error.validationErrors">
              <li v-for="validationError in notification.error.validationErrors" :key="validationError.field">
                <span class="font-weight-medium">{{ $t(`fields.${validationError.field}`) }}: </span>
                <template v-if="validationError.codes.length === 1">
                  {{ $t(`validationError.${validationError.codes[0]}`) }}
                </template>
                <template v-else>
                  <ul>
                    <li v-for="errCode in validationError.codes" :key="`${validationError.field}.${errCode}`">
                      {{ $t(`validationError.${errCode}`) }}
                    </li>
                  </ul>
                </template>
              </li>
            </ul>
          </v-banner-text>
          <v-banner-actions class="ma-0 ml-auto">
            <XrdBtn
              v-if="notification.error.correlationId"
              class="id-button"
              data-test="copy-id-button"
              variant="text"
              prepend-icon="content_copy"
              text="action.copyId"
              @click.prevent="copyId(notification.error.correlationId)"
            />
            <XrdBtn data-test="close-alert" variant="text" text="action.close" @click="close(notification.id)" />
          </v-banner-actions>
        </v-banner>
      </template>
    </v-slide-y-transition>
  </v-container>
</template>

<script lang="ts" setup>
import { PropType, ref } from 'vue';

import { ERROR_CODE_PREFIX, ErrorManager, NotificationId } from '../types';
import { toClipboard } from '../utils';

import XrdBtn from './XrdBtn.vue';

const props = defineProps({
  manager: {
    type: Object as PropType<ErrorManager>,
    required: true,
  },
});

const { errors, remove } = props.manager;
const hideOnLeave = ref(true);

const translatableMetaPrefix = 'tr.';

type Meta = {
  translatable: boolean;
  key: string;
  args: string[];
};

function close(id: NotificationId) {
  try {
    hideOnLeave.value = false;
    remove(id);
  } finally {
    hideOnLeave.value = true;
  }
}

function color(warning?: boolean) {
  return warning ? 'warning' : 'error';
}

function groupMetaData(metaData?: string[]) {
  const groups: Meta[] = [];
  if (metaData) {
    let group: Meta | undefined = undefined;
    for (const meta of metaData) {
      if (meta.startsWith(translatableMetaPrefix)) {
        group = { translatable: true, key: ERROR_CODE_PREFIX + meta, args: [] };
        groups.push(group);
      } else if (group) {
        group.args.push(meta);
      } else {
        groups.push({
          translatable: false,
          key: meta,
          args: [],
        });
      }
    }
  }
  return groups;
}

function copyId(errorId?: string): void {
  if (errorId) {
    toClipboard(errorId);
  }
}
</script>
<style lang="scss" scoped>
.alert:not(:last-child) {
  margin-bottom: 16px;
}
</style>
