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
    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{ $t(title) }}</th>
          <th>{{ $t('keys.id') }}</th>
        </tr>
      </thead>
      <tbody v-for="key in keys" v-bind:key="key.id">
        <tr>
          <td>
            <div class="name-wrap">
              <i class="icon-xrd_key icon clickable" @click="keyClick(key)"></i>
              <div class="clickable-link" @click="keyClick(key)">
                {{ key.name }}
              </div>
            </div>
          </td>
          <td>
            <div class="id-wrap">
              <div class="clickable-link" @click="keyClick(key)">
                {{ key.id }}
              </div>
              <SmallButton
                v-if="canCreateCsr"
                class="table-button-fix"
                :disabled="disableGenerateCsr(key)"
                @click="generateCsr(key)"
                >{{ $t('keys.generateCsr') }}</SmallButton
              >
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import SmallButton from '@/components/ui/SmallButton.vue';
import { Key } from '@/openapi-types';
import { Permissions, PossibleActions } from '@/global';

export default Vue.extend({
  components: {
    SmallButton,
  },
  props: {
    keys: {
      type: Array,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
    tokenLoggedIn: {
      type: Boolean,
    },
    tokenType: {
      type: String,
      required: true,
    },
  },
  computed: {
    canCreateCsr(): boolean {
      return (
        this.$store.getters.hasPermission(Permissions.GENERATE_AUTH_CERT_REQ) ||
        this.$store.getters.hasPermission(Permissions.GENERATE_SIGN_CERT_REQ)
      );
    },
  },
  methods: {
    disableGenerateCsr(key: Key): boolean {
      if (!this.tokenLoggedIn) {
        return true;
      }

      if (
        key.possible_actions?.includes(PossibleActions.GENERATE_AUTH_CSR) ||
        key.possible_actions?.includes(PossibleActions.GENERATE_SIGN_CSR)
      ) {
        return false;
      }

      return true;
    },
    keyClick(key: Key): void {
      this.$emit('key-click', key);
    },
    generateCsr(key: Key): void {
      this.$emit('generate-csr', key);
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';
.icon {
  margin-left: 18px;
  margin-right: 20px;
}

.clickable {
  cursor: pointer;
}

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
  height: 100%;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;

  i.v-icon.mdi-file-document-outline {
    margin-left: 42px;
  }
}

.id-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  align-items: center;
  width: 100%;
}
</style>
