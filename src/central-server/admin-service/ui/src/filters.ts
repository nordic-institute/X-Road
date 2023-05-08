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
import Vue from 'vue';
import { ClientId } from '@/openapi-types';
import { toShortMemberId } from '@/util/helpers';

Vue.filter('capitalize', (value: string): string => {
  if (!value) {
    return '';
  }
  value = value.toString();
  return value.charAt(0).toUpperCase() + value.slice(1);
});

// Add colon for every two characters.  xxxxxx -> xx:xx:xx
Vue.filter('colonize', (value: string): string => {
  if (!value) {
    return '';
  }

  const colonized = value.replace(/(.{2})/g, '$1:');

  if (colonized[colonized.length - 1] === ':') {
    return colonized.slice(0, -1);
  }

  return colonized;
});

// Upper case every word
Vue.filter('upperCaseWords', (value: string): string => {
  if (!value) {
    return '';
  }
  return value
    .toLowerCase()
    .split(' ')
    .map((s) => s.charAt(0).toUpperCase() + s.substring(1))
    .join(' ');
});

// Format date string. Result YYYY-MM-DD.
Vue.filter('formatDate', (value: string): string => {
  const timestamp = Date.parse(value);

  if (isNaN(timestamp)) {
    return '-';
  }

  const date = new Date(value);

  return (
    date.getFullYear() +
    '-' +
    (date.getMonth() + 1).toString().padStart(2, '0') +
    '-' +
    date.getDate().toString().padStart(2, '0')
  );
});

// Format date string. Result YYYY-MM-DD HH:MM.
export const formatDateTime = (value: string): string => {
  const timestamp = Date.parse(value);

  if (isNaN(timestamp)) {
    return '-';
  }

  const date = new Date(value);

  return (
    date.getFullYear() +
    '-' +
    (date.getMonth() + 1).toString().padStart(2, '0') +
    '-' +
    date.getDate().toString().padStart(2, '0') +
    ' ' +
    date.getHours().toString().padStart(2, '0') +
    ':' +
    date.getMinutes().toString().padStart(2, '0')
  );
};

// Format date string. Result YYYY-MM-DD HH:MM:SS.
export const formatDateTimeSeconds = (value: string): string => {
  const timestamp = Date.parse(value);

  if (isNaN(timestamp)) {
    return '-';
  }

  const date = new Date(value);

  return (
    formatDateTime(value) + ':' + date.getSeconds().toString().padStart(2, '0')
  );
};

Vue.filter('formatDateTime', formatDateTime);
Vue.filter('formatDateTimeSeconds', formatDateTimeSeconds);

// Format date string. Result HH:MM.
Vue.filter('formatHoursMins', (value: string): string => {
  const timestamp = Date.parse(value);

  if (isNaN(timestamp)) {
    return '-';
  }

  const date = new Date(value);
  return (
    date.getHours().toString().padStart(2, '0') +
    ':' +
    date.getMinutes().toString().padStart(2, '0')
  );
});

Vue.filter('commaSeparate', (value: string[]) => {
  return value.join(', ');
});

Vue.filter('formatShortMemberId', (value: ClientId) => {
  return toShortMemberId(value);
});
