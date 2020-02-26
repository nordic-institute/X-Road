<template>
  <div class="content">
    {{$t('wizard.token.info')}}
    <v-text-field
      v-model="search"
      :label="$t('wizard.token.tokenName')"
      single-line
      hide-details
      class="search-input"
    >
      <v-icon slot="append">mdi-magnify</v-icon>
    </v-text-field>

    <v-radio-group v-model="tokenGroup">
      <v-radio
        v-for="token in filteredTokens"
        :key="token.id"
        :label="`Token ${token.name}`"
        :value="token"
      ></v-radio>
    </v-radio-group>

    <div class="button-footer">
      <div class="button-group">
        <large-button outlined @click="cancel" :disabled="!disableDone">{{$t('action.cancel')}}</large-button>
      </div>
      <large-button @click="done" :disabled="disableNext">{{$t('action.next')}}</large-button>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { Token } from '@/types';

export default Vue.extend({
  components: {
    LargeButton,
    ValidationObserver,
    ValidationProvider,
  },
  computed: {
    ...mapGetters(['tokens']),

    filteredTokens: {
      get(): Token[] {
        return this.$store.getters.tokensFilteredByName(this.search);
      },
    },

    disableNext() {
      if (this.tokenGroup) {
        return false;
      }
      return true;
    },
  },
  data() {
    return {
      search: undefined,
      disableDone: true,
      tokenGroup: undefined as Token | undefined,
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      if (!this.tokenGroup || !this.tokenGroup.id) {
        return;
      }
      this.$store.dispatch('setCsrTokenId', this.tokenGroup.id);
      this.$emit('done');
    },
    fetchData(): void {
      // Fetch tokens from backend
      this.$store.dispatch('fetchTokens').catch((error) => {
        this.$bus.$emit('show-error', error.message);
      });
    },
    generateCsr(): void {
      this.$store.dispatch('generateCsr').then(
        (response) => {
          this.disableDone = false;
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
  },
  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/shared';

.search-input {
  width: 300px;
}

.generate-row {
  margin-top: 40px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
}

.row-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.label {
  width: 230px;
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.form-input {
  width: 300px;
}

.button-footer {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  border-top: solid 1px $XRoad-Grey40;
  margin-top: 40px;
  padding-top: 30px;
}

.button-group {
  display: flex;
  flex-direction: row;
  align-items: baseline;

  :not(:last-child) {
    margin-right: 20px;
  }
}
</style>

