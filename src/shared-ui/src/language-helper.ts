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
import enSharedMessages from './locales/en.json';
import enValidationMessages from '@vee-validate/i18n/dist/locale/en.json';

interface MessageLoader {
  (language: string): Promise<any>
}

interface LanguageHelper {
  selectLanguage(language: string): Promise<void>;

  loadLanguage(language: string): Promise<void>;

  getCurrentLanguage(): string;
}

export const defaultLanguage = import.meta.env.VITE_I18N_LOCALE || 'en';
export const defaultFallbackLanguage = import.meta.env.VITE_FALLBACK_LOCALE || defaultLanguage;

export function prepareI18n(fallbackMessages: any, ...loaders: MessageLoader[]) {
  const loadedLanguages = new Set(defaultLanguage);

  const _loaders = [loadValidationMessages, loadSharedMessages, ...loaders];

  const enMessages = merge.all([{ validation: enValidationMessages }, enSharedMessages, fallbackMessages]) as any;

  const missigAndFallbackWarn = import.meta.env.VITE_WARN_MISSING_TRANSLATION == 'true';

  const i18n = createI18n({
    legacy: false,
    locale: defaultLanguage,
    fallbackLocale: defaultFallbackLanguage,
    missingWarn: missigAndFallbackWarn,
    fallbackWarn: missigAndFallbackWarn,
    allowComposition: true,
    messages: { en: enMessages },
  });

  async function load(language: string) {
    return Promise.all(_loaders.map(loader => loader(language)))
      .then(msgs => {
        return msgs
      })
      .then((msgs) => merge.all(msgs));
  }

  async function loadLanguage(language: string) {
    if (!loadedLanguages.has(language)) {
      const messages = await load(language);
      i18n.global.setLocaleMessage(language, messages);
      loadedLanguages.add(language)
    }
  }

  async function selectLanguage(language: string) {
    await loadLanguage(language);
    i18n.global.locale.value = language;
  }

  const languageHelper: LanguageHelper = {
    selectLanguage,
    loadLanguage,
    getCurrentLanguage() {
      return i18n.global.locale.value;
    },
  }

  return { i18n, languageHelper };
}

async function loadSharedMessages(language: string) {
  try {
    const module = await import(`./locales/${language}.json`);
    return module.default;
  } catch(e) {
    console.error("Failed to load translations for: " + language);
    return {};
  }
}

async function loadValidationMessages(language: string) {
  try {
    const msg = await import(`../../node_modules/@vee-validate/i18n/dist/locale/${language}.json`);
    return { validation: msg.default };
  } catch(e) {
    console.error("Failed to load translations for: " + language);
    return {}
  }
}

function getUserLanguages(): string[] {
  if (navigator.languages) {
    return [...navigator.languages];
  } else {
    return [navigator.language || defaultLanguage];
  }
}

export function pickDefaultLanguage(supportedLanguages: string[]) {
  if (import.meta.env.VITE_I18N_STOP_USER_LOCALE != 'true') {

    const userLanguages = getUserLanguages()
      .map(lang => lang.replace('_', '-'));
    const lcSupportedLanguages = supportedLanguages
      .map(lang => lang.toLowerCase());


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
