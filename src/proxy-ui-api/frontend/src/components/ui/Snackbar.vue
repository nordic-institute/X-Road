<template>
  <div>
    <!-- Error: raw text  -->
    <v-snackbar
      data-test="error-snackbar"
      v-model="showErrorRaw"
      color="error"
      :timeout="timeout"
    >
      {{ errorMessageRaw }}
      <v-btn data-test="close-snackbar" text @click="closeError()">{{
        $t('action.close')
      }}</v-btn>
    </v-snackbar>

    <!-- Error: localization code -->
    <v-snackbar
      data-test="error-snackbar"
      v-model="showErrorCode"
      color="error"
      :timeout="timeout"
    >
      {{ $t(errorMessageCode) }}
      <v-btn data-test="close-snackbar" text @click="closeError()">{{
        $t('action.close')
      }}</v-btn>
    </v-snackbar>

    <!-- Success: localization code -->
    <v-snackbar
      data-test="success-snackbar"
      v-model="showSuccessCode"
      color="success"
      :timeout="timeout"
    >
      {{ $t(successMessageCode) }}
      <v-btn data-test="close-snackbar" text @click="closeSuccess()">{{
        $t('action.close')
      }}</v-btn>
    </v-snackbar>

    <!-- Success: raw text -->
    <v-snackbar
      data-test="success-snackbar"
      v-model="showSuccessRaw"
      color="success"
      :timeout="timeout"
    >
      {{ successMessageRaw }}
      <v-btn data-test="close-snackbar" text @click="closeSuccess()">{{
        $t('action.close')
      }}</v-btn>
    </v-snackbar>

    <!-- Error: Object. Doesn't close automatically -->
    <v-snackbar
      data-test="indefinite-snackbar"
      v-if="errorObject"
      v-model="showError"
      :timeout="forever"
      color="error"
      multi-line
    >
      <div class="row-wrapper">
        <div>
          {{ $t('error_code.' + errorObject.response.data.error.code) }}
        </div>

        <!-- Show the error metadata if it exists -->
        <template v-if="errorObject.response.data.error.metadata">
          <div
            v-for="meta in errorObject.response.data.error.metadata"
            :key="meta"
          >
            {{ meta }}
          </div>
        </template>
        <!-- Error ID -->
        <div v-if="errorObject.response">
          {{ $t('id') }}:
          {{ errorObject.response.headers['x-road-ui-correlation-id'] }}
        </div>
      </div>

      <template v-if="errorObject.response">
        <v-btn
          outlined
          color="white"
          v-clipboard:copy="
            errorObject.response.headers['x-road-ui-correlation-id']
          "
          >{{ $t('action.copyId') }}
        </v-btn>
      </template>

      <v-btn icon color="white" @click="closeError()">
        <v-icon dark>mdi-close-circle</v-icon>
      </v-btn>
    </v-snackbar>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

export default Vue.extend({
  // Component for snackbar notifications
  computed: {
    ...mapGetters([
      'successMessageCode',
      'successMessageRaw',
      'errorMessageRaw',
      'errorMessageCode',
      'errorObject',
    ]),

    showSuccessCode: {
      get(): string {
        return this.$store.getters.showSuccessCode;
      },
      set(value: string) {
        this.$store.commit('setSuccessCodeVisible', value);
      },
    },
    showSuccessRaw: {
      get(): string {
        return this.$store.getters.showSuccessRaw;
      },
      set(value: string) {
        this.$store.commit('setSuccessRawVisible', value);
      },
    },
    showError: {
      get(): string {
        return this.$store.getters.showErrorObject;
      },
      set(value: string) {
        this.$store.commit('setErrorObjectVisible', value);
      },
    },
    showErrorRaw: {
      get(): string {
        return this.$store.getters.showErrorRaw;
      },
      set(value: string) {
        this.$store.commit('setErrorRawVisible', value);
      },
    },
    showErrorCode: {
      get(): string {
        return this.$store.getters.showErrorCode;
      },
      set(value: string) {
        this.$store.commit('setErrorCodeVisible', value);
      },
    },
  },

  data() {
    return {
      timeout: 2000,
      forever: 0,
    };
  },
  methods: {
    closeSuccess(): void {
      this.$store.commit('setSuccessRawVisible', false);
      this.$store.commit('setSuccessCodeVisible', false);
    },
    closeError(): void {
      this.$store.commit('setErrorRawVisible', false);
      this.$store.commit('setErrorCodeVisible', false);
      this.$store.commit('setErrorObjectVisible', false);
    },
  },
});
</script>

<style lang="scss" scoped>
.row-wrapper {
  display: flex;
  flex-direction: column;
  overflow: auto;
  overflow-wrap: break-word;
}
</style>
