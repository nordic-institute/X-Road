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
    <!-- Error -->
    <v-container
      v-if="errorNotifications && errorNotifications.length > 0"
      fluid
      class="alerts-container"
    >
      <v-alert
        v-for="notification in errorNotifications"
        :key="notification.timeAdded"
        v-model="notification.show"
        data-test="global-alert-global-configuration"
        color="red"
        border="left"
        colored-border
        class="alert"
        icon="icon-Error-notification"
      >
        <div class="row-wrapper-top scrollable identifier-wrap">
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
            <div
              v-else-if="notification.errorObject && errorCode(notification)"
            >
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

          <LargeButton
            v-if="errorId(notification)"
            text
            :outlined="false"
            class="id-button"
            data-test="copy-id-button"
            @click.prevent="copyId(notification)"
            ><v-icon class="xrd-large-button-icon">icon-Copy</v-icon
            >{{ $t('action.copyId') }}</LargeButton
          >

          <div class="buttons">
            <v-btn
              icon
              color="primary"
              data-test="close-snackbar"
              @click="closeError(notification.timeAdded)"
            >
              <v-icon dark>icon-Close</v-icon>
            </v-btn>
          </div>
        </div>
      </v-alert>
    </v-container>
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
  // Component for contextual notifications
  computed: {
    ...mapGetters(['errorNotifications']),
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
@import '~styles/colors';

.alerts-container {
  width: 1000px;
  padding: 0;

  & > * {
    margin-top: 0;
    margin-bottom: 4px;
  }
}

.alert {
  margin-top: 8px;
  border-radius: 4px;
}

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
  margin-left: 0;
  margin-right: auto;
}

.buttons {
  height: 100%;
  display: flex;
  flex-direction: row;
}

.scrollable {
  overflow-y: auto;
  max-height: 300px;
}
</style>
