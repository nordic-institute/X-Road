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
                v-if="hasPermission"
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
    hasPermission(): boolean {
      // Can the user login to the token and see actions
      return this.$store.getters.hasPermission(
        Permissions.ACTIVATE_DEACTIVATE_TOKEN,
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
      this.$emit('keyClick', key);
    },
    generateCsr(key: Key): void {
      this.$emit('generateCsr', key);
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
