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
          <div class="icon-wrapper">
            <v-icon class="icon"> icon-Error-notification</v-icon>
            <div class="row-wrapper">
              <!-- Show error message text -->
              <div v-if="notification.errorMessage">
                {{ notification.errorMessage }}
              </div>

              <!-- Show localised text by id from error object -->
              <div v-else-if="notification.errorCode">
                {{ $t('error_code.' + notification.errorCode) }}
              </div>

              <!-- If error doesn't have a text or localisation key then just print the error object -->
              <div v-else-if="notification.errorObjectAsString">
                {{ notification.errorObjectAsString }}
              </div>

              <!-- Special case for pin code validation -->
              <div v-if="notification.errorCode === 'token_weak_pin'">
                <div>
                  {{
                    $t(`error_code.${notification.metaData[0]}`) +
                    `: ${notification.metaData[1]}`
                  }}
                </div>
                <div>
                  {{
                    $t(`error_code.${notification.metaData[2]}`) +
                    `: ${notification.metaData[3]}`
                  }}
                </div>
              </div>
              <div v-for="meta in notification.metaData" v-else :key="meta">
                {{ meta }}
              </div>

              <!-- Show validation errors -->
              <ul v-if="notification.validationErrors">
                <li
                  v-for="validationError in notification.validationErrors"
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
              <div v-if="notification.errorId">
                {{ $t('alert.id') }}:
                {{ notification.errorId }}
              </div>

              <!-- count -->
              <div v-if="notification.count > 1">
                {{ $t('alert.count') }}
                {{ notification.count }}
              </div>
            </div>
          </div>
          <xrd-button
            v-if="notification.errorId"
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
              data-test="close-alert"
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
import { mapActions, mapState } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { toClipboard } from '@/util/helpers';
import { Notification } from '@/ui-types';

export default Vue.extend({
  // Component for contextual notifications
  computed: {
    ...mapState(notificationsStore, ['errorNotifications']),
  },
  methods: {
    ...mapActions(notificationsStore, ['deleteNotification']),

    closeError(id: number): void {
      this.deleteNotification(id);
    },
    copyId(notification: Notification): void {
      const id = notification.errorId;
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
  padding: 0;

  & > * {
    margin-bottom: 4px;
  }
}

.alert {
  margin-top: 16px;
  border: 2px solid $XRoad-WarmGrey30;
  box-sizing: border-box;
  border-radius: 4px;
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

  .icon {
    margin-right: 12px;
    color: $XRoad-Error;
  }
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
