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
      class="alerts-container px-3"
    >
      <v-alert
        v-for="notification in errorNotifications"
        :key="notification.timeAdded"
        v-model="notification.show"
        data-test="contextual-alert"
        color="red"
        border="left"
        colored-border
        class="alert"
      >
        <div class="row-wrapper-top scrollable identifier-wrap">
          <div class="mr-4">
            <xrd-icon-base color="red"
              ><XrdIconErrorNotification
            /></xrd-icon-base>
          </div>
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
                      v-for="errCode in validationError.errorCodes"
                      :key="`${validationError.field}.${errCode}`"
                    >
                      {{ $t(`validationError.${errCode}`) }}
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

          <xrd-button
            v-if="errorId(notification)"
            text
            :outlined="false"
            class="id-button"
            data-test="copy-id-button"
            @click.prevent="copyId(notification)"
          >
            <xrd-icon-base class="xrd-large-button-icon"
              ><XrdIconCopy
            /></xrd-icon-base>

            {{ $t('action.copyId') }}</xrd-button
          >

          <!-- Handle possible action -->
          <div v-if="notification.action" class="buttons">
            <xrd-button
              text
              color="primary"
              data-test="action-icon-snackbar"
              @click="routeAction(notification)"
            >
              <v-icon dark>{{ notification.action.icon }}</v-icon>
              {{ $t(notification.action.text) }}
            </xrd-button>
          </div>

          <div class="close-button">
            <v-btn
              icon
              color="primary"
              data-test="close-snackbar"
              @click="closeError(notification.timeAdded)"
            >
              <xrd-icon-base><XrdIconClose /></xrd-icon-base>
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
import { StoreTypes } from '@/global';

type ValidationError = {
  field: string;
  errorCodes: string[];
};

export default Vue.extend({
  // Component for contextual notifications
  computed: {
    ...mapGetters({
      errorNotifications: StoreTypes.getters.ERROR_NOTIFICATIONS,
    }),
  },
  methods: {
    errorCode(notification: Notification): string | undefined {
      return notification.errorObject?.response?.data?.error?.code;
    },

    errorMetadata(notification: Notification): string[] {
      return notification.errorObject?.response?.data?.error?.metadata ?? [];
    },

    errorId(notification: Notification): string | undefined {
      return notification.errorObject?.response?.headers[
        'x-road-ui-correlation-id'
      ];
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
      this.$store.commit(StoreTypes.mutations.DELETE_NOTIFICATION, id);
    },
    copyId(notification: Notification): void {
      const id = this.errorId(notification);
      if (id) {
        toClipboard(id);
      }
    },

    routeAction(notification: Notification): void {
      if (notification.action) {
        this.$router.push({
          name: notification.action.route,
        });
      }
      this.closeError(notification.timeAdded);
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

  .alert {
    margin-top: 8px;
    border: 2px solid $XRoad-WarmGrey30;
    box-sizing: border-box;
    border-radius: 4px;

    .row-wrapper-top {
      display: flex;
      flex-direction: row;
      justify-content: flex-start;
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

    .close-button {
      height: 100%;
      margin-left: auto;
      margin-right: 5px;
    }

    .scrollable {
      overflow-y: auto;
      max-height: 300px;
    }
  }
}
</style>
