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
  <div>
    <!-- Success -->
    <v-snackbar
      data-test="success-snackbar"
      v-for="notification in successNotifications"
      :key="notification.timeAdded"
      :timeout="notification.timeout"
      v-model="notification.show"
      color="success"
      multi-line
      @input="closeSuccess(notification.timeAdded)"
    >
      <div class="row-wrapper-top scrollable">
        <div class="row-wrapper">
          <div v-if="notification.successMessageCode">
            {{ $t(notification.successMessageCode) }}
          </div>
          <div v-if="notification.successMessageRaw">
            {{ successMessageRaw }}
          </div>
        </div>
        <v-btn
          icon
          color="white"
          data-test="close-snackbar"
          @click="closeSuccess(notification.timeAdded)"
        >
          <v-icon dark>mdi-close-circle</v-icon>
        </v-btn>
      </div>
    </v-snackbar>

    <!-- Error -->
    <v-snackbar
      data-test="indefinite-snackbar"
      v-for="notification in errorNotifications"
      :key="notification.timeAdded"
      :timeout="notification.timeout"
      v-model="notification.show"
      color="error"
      multi-line
      @input="closeError(notification.timeAdded)"
    >
      <div class="row-wrapper-top scrollable">
        <div class="row-wrapper">
          <!-- Show localised text by id -->
          <div v-if="notification.errorMessageCode">
            {{ $t(notification.errorMessageCode) }}
          </div>

          <!-- Show raw text -->
          <div v-else-if="notification.errorMessageRaw">
            {{ notification.errorMessageRaw }}
          </div>

          <!-- Show localised text by id from error object -->
          <div v-else-if="notification.errorObject && errorCode(notification)">
            {{ $t('error_code.' + errorCode(notification)) }}
          </div>

          <!-- If error doesn't have a text or localisation key then just print the error object -->
          <div v-else-if="notification.errorObject">
            {{ notification.errorObject }}
          </div>

          <!-- Show the error metadata if it exists -->
          <div v-for="meta in errorMetadata(notification)" :key="meta">
            {{ meta }}
          </div>

          <!-- Show validation errors -->
          <ul v-if="hasValidationErrors(notification)">
            <li
              v-for="validationError in validationErrors(notification)"
              :key="validationError.field"
            >
              {{ $t(`fields.${validationError.field}`) }}:
              <template v-if="validationError.errorCodes.length === 1">
                {{ $t(`validationError.${validationError.errorCodes[0]}`) }}
              </template>
              <template v-else>
                <ul>
                  <li
                    v-for="errorCode in validationError.errorCodes"
                    :key="`${validationError.field}.${errorCode}`"
                  >
                    {{ $t(`validationError.${errorCode}`) }}
                  </li>
                </ul>
              </template>
            </li>
          </ul>

          <!-- Error ID -->
          <div v-if="errorId(notification)">
            {{ $t('id') }}:
            {{ errorId(notification) }}
          </div>

          <!-- count -->
          <div v-if="notification.count > 1">
            {{ $t('count') }}
            {{ notification.count }}
          </div>
        </div>

        <div class="buttons">
          <template v-if="errorId(notification)">
            <v-btn
              outlined
              class="id-button"
              color="white"
              data-test="copy-id-button"
              @click.prevent="copyId(notification)"
              >{{ $t('action.copyId') }}
            </v-btn>
          </template>

          <v-btn
            icon
            color="white"
            data-test="close-snackbar"
            @click="closeError(notification.timeAdded)"
          >
            <v-icon dark>mdi-close-circle</v-icon>
          </v-btn>
        </div>
      </div>
    </v-snackbar>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { toClipboard } from '@/util/helpers';
import { Notification } from '@/ui-types';

type ValidationError = {
  field: string;
  errorCodes: string[];
};

export default Vue.extend({
  // Component for snackbar notifications
  computed: {
    ...mapGetters([
      'successMessageCode',
      'successMessageRaw',
      'errorNotifications',
      'successNotifications',
    ]),
  },
  methods: {
    errorCode(notification: Notification): string | undefined {
      if (notification.errorObject?.response?.data?.error?.code) {
        return notification.errorObject.response.data.error.code;
      }

      return undefined;
    },

    errorMetadata(notification: Notification): string[] {
      if (notification.errorObject?.response?.data?.error?.metadata) {
        return notification.errorObject.response.data.error.metadata;
      }

      return [];
    },

    errorId(notification: Notification): string | undefined {
      if (
        notification.errorObject?.response?.headers['x-road-ui-correlation-id']
      ) {
        return notification.errorObject.response.headers[
          'x-road-ui-correlation-id'
        ];
      }

      return undefined;
    },

    hasValidationErrors(notification: Notification): boolean {
      return (
        notification.errorObject?.response?.data?.error?.validation_errors !==
        undefined
      );
    },

    validationErrors(notification: Notification): ValidationError[] {
      const validationErrors =
        notification.errorObject?.response?.data?.error?.validation_errors;
      return Object.keys(validationErrors).map(
        (field) =>
          ({
            field,
            errorCodes: validationErrors[field],
          } as ValidationError),
      );
    },

    closeSuccess(id: number): void {
      this.$store.commit('deleteSuccessNotification', id);
    },
    closeError(id: number): void {
      this.$store.commit('deleteNotification', id);
    },
    copyId(notification: Notification): void {
      const id = this.errorId(notification);
      if (id) {
        toClipboard(id);
      }
    },
  },
});
</script>

<style lang="scss" scoped>
.row-wrapper-top {
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
}

.id-button {
  margin-right: 10px;
}

.buttons {
  height: 100%;
}

.scrollable {
  overflow-y: auto;
  max-height: 300px;
}
</style>
