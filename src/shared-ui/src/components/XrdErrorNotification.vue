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
  <v-alert
    :model-value="notification.show"
    data-test="contextual-alert"
    :color="notificationColor(notification)"
    border="start"
    border-color="error"
    variant="outlined"
    class="alert mb-2"
    :icon="notification.isWarning ? 'icon-Warning' : 'icon-Error-notification'"
    type="error"
  >
    <div class="row-wrapper-top scrollable identifier-wrap">
      <div class="icon-wrapper">
        <div class="row-wrapper">
          <!-- Show error message text -->
          <div v-if="notification.errorMessage">
            {{ notification.errorMessage }}
          </div>

          <!-- Show localised text by id from error object -->
          <div v-else-if="notification.errorCode">
            {{ $t(errorCodePrefix + notification.errorCode) }}
          </div>

          <!-- If error doesn't have a text or localisation key then just print the error object -->
          <div v-else-if="notification.errorObjectAsString">
            {{ notification.errorObjectAsString }}
          </div>

          <div v-for="meta in groupedMetas" :key="meta.key">
            {{ meta.translatable ? $t(meta.key, meta.args) : meta.key }}
          </div>

          <!-- Show validation errors -->
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

          <!-- Error ID -->
          <div v-if="notification.errorId">
            {{ $t('alert.id') + ':' }}
            {{ notification.errorId }}
          </div>

          <!-- count -->
          <div v-if="notification.count > 1 && !notification.isWarning">
            {{ $t('alert.count') }}
            {{ notification.count }}
          </div>
        </div>
      </div>
      <xrd-button v-if="notification.errorId" text class="id-button" data-test="copy-id-button" @click.prevent="copyId(notification)">
        <xrd-icon-base class="xrd-large-button-icon">
          <XrdIconCopy />
        </xrd-icon-base>
        {{ $t('action.copyId') }}
      </xrd-button>
    </div>
    <template #close>
      <v-btn color="primary" data-test="close-alert" variant="plain" @click="emit('close', notification)">
        <xrd-icon-base>
          <xrd-icon-close />
        </xrd-icon-base>
      </v-btn>
    </template>
  </v-alert>
</template>

<script lang="ts" setup>
import { helper, Colors } from '../utils';
import { computed, PropType } from 'vue';

const errorCodePrefix = 'error_code.';
const translatableMetaPrefix = 'tr.';

const props = defineProps({
  notification: {
    type: Object as PropType<Notification>,
    required: true,
  },
});

const emit = defineEmits<{ (e: 'close', value: Notification): void }>();

type Notification = {
  show: boolean;
  isWarning?: boolean;
  errorMessage?: string;
  errorCode?: string;
  errorObjectAsString?: string;
  errorId?: string;
  validationErrors?: ValidationError[];
  metaData?: string[];
  count: number;
  timeAdded: number;
};

type ValidationError = {
  field: string;
  errorCodes: string[];
};

type Meta = {
  translatable: boolean;
  key: string;
  args: string[];
};

const groupedMetas = computed(() => {
  const groups: Meta[] = [];
  let group: Meta | undefined = undefined;
  for (const meta of props.notification.metaData || []) {
    if (meta.startsWith(translatableMetaPrefix)) {
      group = { translatable: true, key: errorCodePrefix + meta, args: [] };
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
  return groups;
});

function notificationColor(notification: Notification) {
  return notification.isWarning ? Colors.Warning : Colors.Error;
}

function copyId(notification: Notification): void {
  const id = notification.errorId;
  if (id) {
    helper.toClipboard(id);
  }
}
</script>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/colors';

.alert {
  margin-top: 16px;
  border: 2px solid colors.$WarmGrey30;
  box-sizing: border-box;
  border-radius: 4px;
  background-color: colors.$White100;
}

.row-wrapper-top {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
}

.icon-wrapper {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
}

.row-wrapper {
  display: flex;
  flex-direction: column;
  overflow: auto;
  overflow-wrap: break-word;
  justify-content: center;
  margin-right: 30px;
  color: colors.$Black100;
}

.id-button {
  margin-left: 0;
  margin-right: auto;
}

.scrollable {
  overflow-y: auto;
  max-height: 300px;
}
</style>
