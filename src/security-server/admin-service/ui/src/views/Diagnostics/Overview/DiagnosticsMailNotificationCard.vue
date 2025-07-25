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
  <v-card variant="flat" class="xrd-card diagnostic-card">
    <v-card-title class="text-h5" data-test="diagnostics-mail-notification">
      {{ $t('diagnostics.mailNotificationConfiguration.title')
      }}<HelpButton
        class="help-icon"
        help-text="diagnostics.mailNotificationConfiguration.help.description"
        help-title="diagnostics.mailNotificationConfiguration.help.title"
      />
    </v-card-title>

    <v-card-text class="xrd-card-text">
      <table class="xrd-table">
        <thead>
          <tr>
            <th>
              {{
                $t('diagnostics.mailNotificationConfiguration.configuration')
              }}
            </th>
            <th>
              {{ $t('diagnostics.mailNotificationConfiguration.types') }}
            </th>
            <th>
              {{
                $t('diagnostics.mailNotificationConfiguration.recipientsEmails')
              }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td
              class="vertical-align-top pt-4"
              data-test="mail-notification-configuration-status"
            >
              <div
                v-if="
                  mailNotificationStatus.configuration_present !== undefined
                "
                class="d-flex font-weight-bold"
              >
                <xrd-status-icon
                  v-if="mailNotificationStatus.configuration_present"
                  status="ok"
                />
                <xrd-status-icon v-else status="error" />
                {{
                  $t(
                    `diagnostics.mailNotificationConfiguration.confStatus.${mailNotificationStatus.configuration_present}`,
                  )
                }}
              </div>
            </td>
            <td class="vertical-align-top">
              <div
                v-for="mailNotificationType in Object.keys(
                  MailNotificationType,
                )"
                :key="mailNotificationType"
              >
                <p
                  v-if="
                    mailNotificationStatus.enabled_notifications !== undefined
                  "
                  class="my-4 d-flex"
                >
                  {{
                    $t(
                      `diagnostics.mailNotificationConfiguration.type.${mailNotificationType}`,
                    ) + ':'
                  }}
                  &nbsp;
                  <xrd-status-icon
                    v-if="
                      mailNotificationStatus.enabled_notifications.includes(
                        mailNotificationType,
                      )
                    "
                    status="ok"
                  />
                  <xrd-status-icon v-else status="ok-disabled" />
                  <span
                    class="font-weight-bold"
                    :data-test="`enabled-${mailNotificationType}`"
                  >
                    {{
                      $t(
                        `diagnostics.mailNotificationConfiguration.enabled.${mailNotificationStatus.enabled_notifications.includes(mailNotificationType)}`,
                      )
                    }}
                  </span>
                </p>
              </div>
            </td>
            <td
              class="vertical-align-top pt-2 fixed-width"
              data-test="mail-notification-recipients"
            >
              <div
                v-for="recipient in mailNotificationStatus.recipients_emails"
                :key="recipient"
                class="recipient-wrapper"
              >
                {{ recipient }}
                <xrd-button
                  v-if="mailNotificationStatus.configuration_present"
                  large
                  variant="text"
                  data-test="send-test-mail"
                  @click="sendTestMailNotification(recipient)"
                >
                  {{ $t('diagnostics.mailNotificationConfiguration.sentTestMail') }}
                </xrd-button>

                <div class="alert-container">
                  <v-alert
                    v-if="testMailStatuses[recipient]"
                    class="test-alert"
                    border="start"
                    :type="testMailStatuses[recipient].type"
                    variant="outlined"
                    density="compact"
                    data-test="test-mail-result"
                  >
                    {{ testMailStatuses[recipient].text }}
                  </v-alert>
                </div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </v-card-text>
  </v-card>
</template>

<script lang="ts">
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useMail } from '@/store/modules/mail';
import { defineComponent } from 'vue';
import HelpButton from '@/components/ui/HelpButton.vue';
import { MailNotificationType } from '@/openapi-types';

type TestMailStatuses = {
  [key: string]: {
    type: string;
    text: string;
  };
};

export default defineComponent({
  components: {
    HelpButton,
  },
  data() {
    return {
      testMailStatuses: {} as TestMailStatuses,
    };
  },
  computed: {
    MailNotificationType() {
      return MailNotificationType;
    },
    ...mapState(useMail, ['mailNotificationStatus']),
  },
  created() {
    this.fetchMailNotificationStatus().catch((error) => {
      this.showError(error);
    });
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    ...mapActions(useMail, ['fetchMailNotificationStatus', 'sendTestMail']),
    sendTestMailNotification(recipient: string) {
      this.sendTestMail(recipient.substring(recipient.indexOf(' ')))
        .then((resp) => {
          this.testMailStatuses = {
            ...this.testMailStatuses,
            [recipient]: {
              type: resp.data.status,
              text: resp.data.text,
            },
          };
        })
        .catch((error) => {
          this.testMailStatuses = {
            ...this.testMailStatuses,
            [recipient]: {
              type: 'error',
              text: `Error: ${error.message}`,
            },
          };
        });
    },
  },
});
</script>
<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/colors';
@use '@niis/shared-ui/src/assets/tables';

.xrd-card-text {
  padding-left: 0;
  padding-right: 0;
}

.diagnostic-card {
  width: 100%;
  margin-bottom: 30px;

  &:first-of-type {
    margin-top: 40px;
  }
}

.vertical-align-top {
  vertical-align: top;
}

.help-icon {
  display: inline-block;
}

.fixed-width {
  width: 40%;
  max-width: 40%;
  word-break: break-word;
}

.recipient-wrapper {
  word-break: break-word;
}

.alert-container {
  max-width: 100%;
  overflow-wrap: break-word;
  white-space: normal;
}

.test-alert {
  margin-top: 0.5rem;
  margin-right: 0.5rem;
  word-break: break-word;
  white-space: normal;
  overflow-wrap: break-word;
}
</style>
