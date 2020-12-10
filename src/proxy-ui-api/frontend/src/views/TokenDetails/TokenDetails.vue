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
  <div class="xrd-tab-max-width detail-view-outer">
    <ValidationObserver ref="form" v-slot="{ invalid }">
      <div class="detail-view-content">
        <subViewTitle :title="$t('keys.tokenDetails')" @close="close" />

        <div class="edit-row">
          <div>{{ $t('keys.friendlyName') }}</div>
          <ValidationProvider
            rules="required"
            name="keys.friendlyName"
            v-slot="{ errors }"
            class="validation-provider"
          >
            <v-text-field
              v-model="token.name"
              single-line
              class="code-input"
              name="keys.friendlyName"
              type="text"
              outlined
              :maxlength="255"
              :error-messages="errors"
              :loading="loading"
              :disabled="!(hasEditPermission && canEditName())"
              @input="touched = true"
              autofocus
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div>
          <h3 class="info-title">{{ $t('keys.tokenInfo') }}</h3>
          <div class="info-row">
            <div class="row-title">{{ $t('keys.tokenId') }}</div>
            <div class="row-data">{{ token.id }}</div>
          </div>
          <div class="info-row">
            <div class="row-title">{{ $t('keys.type') }}</div>
            <div class="row-data">{{ token.type }}</div>
          </div>
        </div>
      </div>
      <div class="footer-button-wrap">
        <large-button @click="close()" outlined>{{
          $t('action.cancel')
        }}</large-button>
        <large-button
          :loading="saveBusy"
          @click="save()"
          :disabled="!touched || invalid"
          >{{ $t('action.save') }}</large-button
        >
      </div>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
/***
 * Component for showing the details of a token.
 */
import Vue from 'vue';
import * as api from '@/util/api';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { Permissions } from '@/global';
import { PossibleAction, Token } from '@/openapi-types';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  computed: {
    hasEditPermission(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_TOKEN_FRIENDLY_NAME,
      );
    },
  },
  data() {
    return {
      touched: false,
      saveBusy: false,
      loading: false,
      token: {} as Token,
    };
  },
  methods: {
    close(): void {
      this.$router.go(-1);
    },

    save(): void {
      this.saveBusy = true;

      api
        .patch(`/tokens/${encodePathParameter(this.id)}`, this.token)
        .then(() => {
          this.$store.dispatch('showSuccess', 'keys.tokenSaved');
          this.$router.go(-1);
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.saveBusy = false;
        });
    },

    fetchData(): void {
      this.loading = true;
      api
        .get<Token>(`/tokens/${encodePathParameter(this.id)}`)
        .then((res) => {
          this.token = res.data;
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.loading = false;
        });
    },

    canEditName(): boolean {
      return (
        this.token?.possible_actions?.includes(
          PossibleAction.EDIT_FRIENDLY_NAME,
        ) ?? false
      );
    },
  },
  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/detail-views';

.code-input {
  width: 450px;
}
</style>
