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
  <tr>
    <td>
      <div class="name-wrap-top">
        <icon-base icon-name="key" class="key-icon"><icon-key /></icon-base>

        <div class="clickable-link identifier-wrap" @click="keyClick">
          <span v-if="!tokenKey.name || tokenKey.name === ''">{{
            tokenKey.id
          }}</span>
          <span v-else>{{ tokenKey.name }}</span>
        </div>
      </div>
    </td>
    <td colspan="4"></td>
    <td class="td-align-right">
      <LargeButton
        v-if="showGenerateCsr"
        class="table-button-fix"
        :outlined="false"
        text
        :disabled="disableGenerateCsr"
        @click="generateCsr"
        >{{ $t('keys.generateCsr') }}</LargeButton
      >
    </td>
  </tr>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import { Prop } from 'vue/types/options';
import { Key, PossibleAction, TokenCertificate } from '@/openapi-types';
import { Permissions } from '@/global';
import IconBase from '@/components/ui/icons/IconBase.vue';
import IconKey from '@/components/ui/icons/IconKey.vue';
export default Vue.extend({
  props: {
    tokenKey: {
      type: Object as Prop<Key>,
      required: true,
    },
    tokenLoggedIn: {
      type: Boolean,
    },
  },
  components: {
    IconKey,
    IconBase,
  },
  computed: {
    showGenerateCsr(): boolean {
      // Check if the user has permission to see generate csr action
      if (this.tokenKey.usage === 'AUTHENTICATION') {
        return this.$store.getters.hasPermission(
          Permissions.GENERATE_AUTH_CERT_REQ,
        );
      }
      // If key usage is not auth then it has to be sign
      return this.$store.getters.hasPermission(
        Permissions.GENERATE_SIGN_CERT_REQ,
      );
    },

    disableGenerateCsr(): boolean {
      // Check if the generate csr action should be disabled
      if (
        this.tokenKey.possible_actions?.includes(
          PossibleAction.GENERATE_AUTH_CSR,
        ) ||
        this.tokenKey.possible_actions?.includes(
          PossibleAction.GENERATE_SIGN_CSR,
        )
      ) {
        return false;
      }

      return true;
    },
  },
  methods: {
    keyClick(): void {
      this.$emit('key-click');
    },
    certificateClick(cert: TokenCertificate, key: Key): void {
      this.$emit('certificate-click', { cert, key });
    },
    generateCsr(): void {
      this.$emit('generate-csr');
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

.no-border {
  border-bottom-width: 0 !important;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}

.td-align-right {
  text-align: right;
}

.clickable-link {
  color: $XRoad-Purple100;
  cursor: pointer;
}

.key-icon {
  margin-right: 18px;
  color: $XRoad-Purple100;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;

  i.v-icon.mdi-file-document-outline {
    margin-left: 42px;
  }
}

.name-wrap-top {
  @extend .name-wrap;
  align-content: center;
  margin-top: 5px;
  margin-bottom: 5px;
}
</style>
