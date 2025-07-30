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
  <v-banner
    v-for="notification in notifications.contextErrors"
    :key="notification.id"
    data-test="contextual-alert"
    class="my-4 border border-s-xl"
    icon="error"
    :color="color(notification.error.warning)"
    bg-color="background"
    :border="color(notification.error.warning)"
    rounded
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
      <p v-if="notification.count > 1 && !notification.error.warning">
        {{ $t('alert.count') }}
        {{ notification.count }}
      </p>

      <!-- Show validation errors -->
      <!--
      <ul v-if="notification.validationErrors">
        <li v-for="validationError in notification.validationErrors" :key="validationError.field">
          {{ $t(`fields.${validationError.field}`) + ':' }}
          <template v-if="validationError.errorCodes.length === 1">
            {{ $t(`validationError.${validationError.errorCodes[0]}`) }}
          </template>
          <template v-else>
            <ul>
              <li v-for="errCode in validationError.errorCodes" :key="`${validationError.field}.${errCode}`">
                {{ $t(`validationError.${errCode}`) }}
              </li>
            </ul>
          </template>
        </li>
      </ul>
-->
    </v-banner-text>
    <template #actions>
      <XrdBtn
        v-if="notification.error.correlationId"
        class="id-button"
        data-test="copy-id-button"
        variant="text"
        prepend-icon="content_copy"
        @click.prevent="copyId(notification.error.correlationId)"
      >
        {{ $t('action.copyId') }}
      </XrdBtn>
      <XrdBtn data-test="close-alert" variant="text" @click="notifications.remove(notification.id)">
        {{ $t('action.close') }}
      </XrdBtn>
    </template>
  </v-banner>
</template>

<script lang="ts" setup>
import { helper } from '../utils';
import { useNotifications, ERROR_CODE_PREFIX } from '../stores';
import XrdBtn from './XrdBtn.vue';

const translatableMetaPrefix = 'tr.';

const notifications = useNotifications();

type Meta = {
  translatable: boolean;
  key: string;
  args: string[];
};

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
    helper.toClipboard(errorId);
  }
}
</script>

<style lang="scss" scoped>
</style>
