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
import enValidationMessages from '@vee-validate/i18n/dist/locale/en.json';
import merge from 'deepmerge';
import { messages } from '@niis/shared-ui';
import enAppMessages from '@/locales/en.json';

const loadedLanguages = new Set('en');
export const availableLanguages = ['en'];

const defaultLanguage = import.meta.env.VITE_I18N_LOCALE || 'en';
const defaultFallbackLanguage = import.meta.env.VITE_FALLBACK_LOCALE || 'en';

const sharedLanguageMessages = {
  en: messages.en,
};
const defaultLanguagePack = merge.all([
  { validation: enValidationMessages },
  sharedLanguageMessages.en,
  enAppMessages,
]);

// Initialize i18n instance with default configuration
export const i18n = createI18n({
  legacy: false,
  locale: defaultLanguage,
  fallbackLocale: defaultFallbackLanguage,
  silentFallbackWarn: true,
  allowComposition: true,
  messages: { en: defaultLanguagePack },
});

// Sets the active language, loading language pack if necessary
export async function setLanguage(language) {
  await loadLanguagePackIfNeeded(language);
  i18n.global.locale.value = language;
}

// Loads language pack if it's not already loaded
async function loadLanguagePackIfNeeded(language) {
  if (!loadedLanguages.has(language)) {
    const messages = await fetchLanguageMessages(language);
    const languagePack = mergeLanguageMessages(messages);
    i18n.global.setLocaleMessage(language, languagePack);
    loadedLanguages.add(language);
  }
}

// Fetches all language-specific messages for the given language
async function fetchLanguageMessages(language) {
  const appMessagesPromise = import(`@/locales/${language}.json`).then(
    (module) => module.default,
  );

  const [appMessages, validationMessages, sharedMessages] = await Promise.all([
    appMessagesPromise,
    loadValidationMessages(language),
    loadSharedMessages(language),
  ]);

  return { appMessages, validationMessages, sharedMessages };
}

// Loads validation messages, with fallback to English if not available
async function loadValidationMessages(language) {
  try {
    const messages = await import(
      `@vee-validate/i18n/dist/locale/${language}.json`
    );
    return messages.default;
  } catch {
    return enValidationMessages;
  }
}

// Loads shared messages based on language
function loadSharedMessages(language) {
  return sharedLanguageMessages[language] || sharedLanguageMessages.en;
}

// Merges application, validation, and shared messages into a single pack
function mergeLanguageMessages({
  appMessages,
  validationMessages,
  sharedMessages,
}) {
  return merge.all([
    { validation: validationMessages },
    sharedMessages,
    appMessages,
  ]);
}
