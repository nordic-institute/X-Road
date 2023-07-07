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
  <xrd-sub-view-container>
    <v-dialog v-if="true" :value="true" width="500" persistent>
      <ValidationObserver ref="initializationForm" v-slot="{ invalid }">
        <v-card class="xrd-card">
          <v-card-title>
            <span class="headline">
              {{ $t('members.member.details.deleteMember') }}
            </span>
          </v-card-title>
          <v-card-text class="pt-4" data-test="delete-member">
            <i18n path="members.member.details.confirmDelete">
              <template #member>
                <b>{{ member.member_name }}</b>
              </template>
            </i18n>
            <div class="dlg-input-width pt-4">
              <ValidationProvider
                v-slot="{ errors }"
                name="memberCode"
                :rules="{ required: true, is: member.client_id.member_code }"
                data-test="instance-identifier--validation"
              >
                <v-text-field
                  v-model="enteredCode"
                  outlined
                  :label="$t('members.member.details.enterCode')"
                  autofocus
                  data-test="member-code"
                  :error-messages="errors"
                ></v-text-field>
              </ValidationProvider>
            </div>
          </v-card-text>
          <v-card-actions class="xrd-card-actions">
            <v-spacer></v-spacer>
            <xrd-button
              outlined
              :disabled="loading"
              data-test="dialog-cancel-button"
              @click="cancelDelete()"
            >
              {{ $t('action.cancel') }}
            </xrd-button>
            <xrd-button
              :disabled="invalid || loading"
              data-test="dialog-delete-button"
              @click="proceedDelete()"
            >
              {{ $t('action.delete') }}
            </xrd-button>
          </v-card-actions>
        </v-card>
      </ValidationObserver>
    </v-dialog>
  </xrd-sub-view-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import { Client } from '@/openapi-types';
import { mapActions, mapStores } from 'pinia';
import { memberStore } from '@/store/modules/members';
import { toIdentifier } from '@/util/helpers';
import { notificationsStore } from '@/store/modules/notifications';

export default Vue.extend({
  name: 'MemberDeleteDialog',
  components: {
    ValidationObserver,
    ValidationProvider,
  },
  props: {
    member: {
      type: Object as () => Client,
      required: true,
    },
  },
  data() {
    return { loading: false, enteredCode: '' };
  },
  computed: {
    ...mapStores(memberStore),
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancelDelete(): void {
      this.enteredCode = '';
      this.$emit('cancel');
    },
    proceedDelete(): void {
      this.loading = true;
      this.memberStore
        .deleteById(toIdentifier(this.member.client_id))
        .then(() => {
          this.$emit('deleted');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
