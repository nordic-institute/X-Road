import { defineStore } from 'pinia';
import { i18n } from '@/plugins/i18n';

export const useLocale = defineStore('locale', {
  state: () => ({
    locale: import.meta.env.VITE_I18N_LOCALE || ('en' as string),
  }),

  persist: {
    storage: localStorage,
  },

  getters: {
    getLocale(state): string {
      return state.locale;
    },
  },

  actions: {
    switchLocale(locale: string) {
      this.locale = locale;
      i18n.global.locale.value = locale;
    },
  },
});
