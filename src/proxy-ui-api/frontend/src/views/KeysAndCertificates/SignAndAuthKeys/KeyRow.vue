<template>
  <tr>
    <td class="name-wrap-top no-border">
      <i class="icon-xrd_key icon clickable" @click="keyClick"></i>
      <div class="clickable-link" @click="keyClick">
        {{ tokenKey.name }}
      </div>
    </td>
    <td class="no-border" colspan="4"></td>
    <td class="no-border td-align-right">
      <SmallButton
        v-if="hasPermission"
        class="table-button-fix"
        :disabled="disableGenerateCsr(tokenKey)"
        @click="generateCsr"
        >{{ $t('keys.generateCsr') }}</SmallButton
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

import SmallButton from '@/components/ui/SmallButton.vue';

import { Key, TokenCertificate } from '@/openapi-types';
import { PossibleActions } from '@/global';

export default Vue.extend({
  components: {
    SmallButton,
  },
  props: {
    tokenKey: {
      type: Object as Prop<Key>,
      required: true,
    },
    hasPermission: {
      type: Boolean,
    },
    tokenLoggedIn: {
      type: Boolean,
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

    keyClick(): void {
      this.$emit('keyClick');
    },
    certificateClick(cert: TokenCertificate, key: Key): void {
      this.$emit('certificateClick', { cert, key });
    },
    generateCsr(): void {
      this.$emit('generateCsr');
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
  text-decoration: underline;
  cursor: pointer;
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
  margin-top: 18px;
  margin-bottom: 5px;
}
</style>
