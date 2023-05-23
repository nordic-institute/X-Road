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
      <v-card class="xrd-card">
        <v-card-title>
          <span class="headline">
            {{ $t('members.member.subsystems.deleteSubsystem') }}
          </span>
        </v-card-title>
        <v-card-text class="pt-4" data-test="delete-subsystem">
          <i18n path="members.member.subsystems.areYouSureDelete">
            <template #subsystemCode>
              <b>{{ subsystemCode }}</b>
            </template>
            <template #memberId>
              <b>{{ currentMember.client_id | formatShortMemberId }}</b>
            </template>
          </i18n>
        </v-card-text>
        <v-card-actions class="xrd-card-actions">
          <v-spacer></v-spacer>
          <xrd-button
            outlined
            :disabled="loading"
            data-test="dialog-cancel-button"
            @click="cancel()"
          >
            {{ $t('action.cancel') }}
          </xrd-button>
          <xrd-button
            :disabled="loading"
            data-test="dialog-delete-button"
            @click="deleteSubsystem()"
          >
            {{ $t('action.delete') }}
          </xrd-button>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </xrd-sub-view-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { clientStore } from '@/store/modules/clients';
import { memberStore } from '@/store/modules/members';
import { systemStore } from '@/store/modules/system';
import { notificationsStore } from '@/store/modules/notifications';
import { subsystemStore } from '@/store/modules/subsystems';
import { Client } from '@/openapi-types';
import { toIdentifier } from '@/util/helpers';

export default Vue.extend({
  name: 'DeleteMemberSubsystemDialog',
  props: {
    showDialog: {
      type: Boolean,
      required: true,
    },
    subsystemCode: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      currentMember: {} as Client,
    };
  },
  computed: {
    ...mapStores(clientStore, memberStore, subsystemStore),
    ...mapState(systemStore, ['getSystemStatus']),
  },
  created() {
    this.currentMember = this.memberStore.$state.currentMember as Client;
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancel(): void {
      this.$emit('cancel');
    },
    deleteSubsystem(): void {
      this.loading = true;
      this.subsystemStore
        .deleteById(
          toIdentifier(this.currentMember.client_id) + ':' + this.subsystemCode,
        )
        .then(() => {
          this.showSuccess(
            this.$t('members.member.subsystems.subsystemSuccessfullyDeleted', {
              subsystemCode: this.subsystemCode,
            }),
          );
          this.$emit('deletedSubsystem');
        })
        .catch((error) => {
          this.showError(error);
          this.$emit('cancel');
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
});
</script>
