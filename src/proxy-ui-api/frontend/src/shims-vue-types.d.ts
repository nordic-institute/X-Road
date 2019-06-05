import Vue from 'vue';
import { ErrorBag } from 'vee-validate';

declare module 'vue/types/vue' {
  interface Vue {
    $bus: Vue;
    errors: ErrorBag;
  }
}
