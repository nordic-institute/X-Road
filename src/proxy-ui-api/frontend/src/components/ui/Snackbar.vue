<template>
  <div>
    <v-snackbar id="error-snackbar" v-model="showErrorMessage" color="error" :timeout="timeout">
      {{ message }}
      <v-btn id="close-snackbar" text @click="closeErrorMessage()">{{$t('action.close')}}</v-btn>
    </v-snackbar>

    <v-snackbar id="success-snackbar" v-model="showSuccess" color="success" :timeout="timeout">
      {{ $t(message) }}
      <v-btn id="close-snackbar" text @click="closeSuccess()">{{$t('action.close')}}</v-btn>
    </v-snackbar>

    <v-snackbar
      id="indefinite-snackbar"
      v-if="errorObject"
      v-model="showError"
      :timeout="forever"
      color="error"
    >
      {{ errorObject.message }}
      <br />
      ID: {{ errorObject.response.headers['x-road-ui-correlation-id'] }}
      <v-btn
        data-test="snackbar-yes-button"
        text
        @click="closeError()"
        outlined
      >{{$t('action.close')}}</v-btn>
    </v-snackbar>

    <!--
    <v-snackbar id="indefinite-snackbar" v-model="indefinite" :timeout="0">
      {{ message }}
      <v-btn
        data-test="snackbar-no-button"
        text
        @click="indefinite = false"
        outlined
      >{{$t('action.no')}}</v-btn>
      <v-btn data-test="snackbar-yes-button" text @click="yesAction()" outlined>{{$t('action.yes')}}</v-btn>
    </v-snackbar>
    -->
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';

export default Vue.extend({
  // Wrapper for vuetify snackbar component
  computed: {
    ...mapGetters(['message', 'errorObject']),

    showSuccess: {
      get(): string {
        return this.$store.getters.showSuccess;
      },
      set(value: string) {
        this.$store.commit('setSuccess', value);
      },
    },
    showError: {
      get(): string {
        return this.$store.getters.showError;
      },
      set(value: string) {
        this.$store.commit('setError', value);
      },
    },
    showErrorMessage: {
      get(): string {
        return this.$store.getters.showErrorMessage;
      },
      set(value: string) {
        this.$store.commit('setErrorMessage', value);
      },
    },
  },

  data() {
    return {
      indefinite: false,
      timeout: 2000,
      forever: 0,
    };
  },
  methods: {
    showIndefinite(message: string): void {
      this.message = message;
      this.indefinite = true;
    },
    closeSuccess(): void {
      this.$store.commit('setSuccess', false);
    },
    closeError(): void {
      this.$store.commit('setError', false);
    },
    closeErrorMessage(): void {
      this.$store.commit('setErrorMessage', false);
    },
  },
});
</script>
