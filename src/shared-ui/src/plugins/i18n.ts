/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import { createI18n } from 'vue-i18n';
import merge from 'deepmerge';
import { nextTick, Plugin, ref, Ref } from 'vue';
import axios from 'axios';

const defaultLanguage = import.meta.env.VITE_I18N_LOCALE || 'en';
const defaultFallbackLanguage = import.meta.env.VITE_FALLBACK_LOCALE || defaultLanguage;

const missingAndFallbackWarn = import.meta.env.VITE_WARN_MISSING_TRANSLATION == 'true';

const supportedLanguages = [] as string[];
const currentLanguage = ref(defaultLanguage);
const messageLoaders = [] as MessageLoader[];
const loadedLanguages = new Set();

export const i18n = createI18n({
  legacy: false,
  locale: defaultLanguage,
  fallbackLocale: defaultFallbackLanguage,
  missingWarn: missingAndFallbackWarn,
  fallbackWarn: missingAndFallbackWarn,
  allowComposition: true,
});

async function selectLanguage(language: string) {
  if (i18n.global.locale.value !== language) {
    await loadLanguage(language);
    i18n.global.locale.value = language;

    axios.defaults.headers.common['Accept-Language'] = language;
    document.querySelector('html')?.setAttribute('lang', language);
    localStorage.language = language;
    currentLanguage.value = language;
  }
  return nextTick();
}

export function useLanguageHelper(): LanguageHelper {
  return { selectLanguage, currentLanguage, supportedLanguages };
}

export async function createLanguageHelper(languages: string[], messageLoader: MessageLoader): Promise<Plugin> {
  messageLoaders.push(messageLoader);
  supportedLanguages.push(...languages);

  await loadLanguage(defaultLanguage);
  if (!localStorage.language || !supportedLanguages.includes(localStorage.language)) {
    localStorage.language = pickDefaultLanguage();
  }

  await selectLanguage(localStorage.language);

  return {
    install(app) {
      app.use(i18n);
    },
  };
}

async function load(language: string) {
  const loaders = [loadValidationMessages, loadVuetifyMessages, loadSharedMessages, ...messageLoaders];
  return Promise.all(loaders.map((loader) => loader(language)))
    .then((msgs) => {
      return msgs;
    })
    .then((msgs) => merge.all(msgs));
}

async function loadLanguage(language: string) {
  if (!loadedLanguages.has(language)) {
    const messages = await load(language);
    i18n.global.setLocaleMessage(language, messages);
    loadedLanguages.add(language);
  }
}

function pickDefaultLanguage() {
  if (import.meta.env.VITE_I18N_STOP_USER_LOCALE != 'true') {
    const userLanguages = getUserLanguages().map((lang) => lang.replace('_', '-'));
    const lcSupportedLanguages = supportedLanguages.map((lang) => lang.toLowerCase());

    for (const lang of userLanguages) {
      //language+region(if present) match
      if (lcSupportedLanguages.includes(lang)) {
        return lang;
      }
      //just language match
      const langCode = lang.split('-')[0];
      if (lcSupportedLanguages.includes(langCode)) {
        return langCode;
      }
    }
  }

  return defaultLanguage;
}

async function loadSharedMessages(language: string) {
  try {
    const module = await import(`../locales/${language}.json`);
    return module.default;
  } catch (e) {
    // eslint-disable-next-line no-console
    console.warn('Failed to load shared translations for: ' + language);
    return {};
  }
}

async function loadValidationMessages(language: string) {
  try {
    const msg = await import(`../../../node_modules/@vee-validate/i18n/dist/locale/${language}.json`);
    return { validation: msg.default };
  } catch (e) {
    // eslint-disable-next-line no-console
    console.warn('Failed to load veeValidate translations for: ' + language);
    return {};
  }
}

async function loadVuetifyMessages(language: string) {
  try {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const locales = (await import('vuetify/locale')) as any;
    if (!locales[language]) {
      // eslint-disable-next-line no-console
      console.warn('Missing Vuetify translations for: ' + language);
    }
    return { $vuetify: locales[language] || {} };
  } catch (e) {
    // eslint-disable-next-line no-console
    console.warn('Failed to load Vuetify translations for: ' + language);
    return {};
  }
}

function getUserLanguages(): string[] {
  if (navigator.languages) {
    return [...navigator.languages];
  } else {
    return [navigator.language || defaultLanguage];
  }
}

interface MessageLoader {
  (language: string): Promise<Translations>;
}

type Translation = string | Record<string, string>;
type Translations = Record<string, Translation>;

interface LanguageHelper {
  selectLanguage(language: string): Promise<void>;

  currentLanguage: Ref<string>;
  supportedLanguages: ReadonlyArray<string>;
}
