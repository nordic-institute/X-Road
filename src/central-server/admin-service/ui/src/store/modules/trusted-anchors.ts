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

import { defineStore } from 'pinia';
import { saveResponseAsFile } from '@/util/helpers';
import axios from 'axios';
import { TrustedAnchor } from '@/openapi-types';

export const useTrustedAnchor = defineStore('trustedAnchor', {
  actions: {
    fetchTrustedAnchors() {
      return axios.get<TrustedAnchor[]>('/trusted-anchors').catch((error) => {
        throw error;
      });
    },
    previewTrustedAnchors(file: File) {
      const formData = new FormData();
      formData.append('anchor', file, file.name);
      const config = {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      };
      return axios
        .post<TrustedAnchor>('/trusted-anchors/preview', formData, config)
        .catch((error) => {
          throw error;
        });
    },
    uploadTrustedAnchor(file: File) {
      const formData = new FormData();
      formData.append('anchor', file, file.name);
      const config = {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      };
      return axios
        .post<TrustedAnchor>('/trusted-anchors', formData, config)
        .catch((error) => {
          throw error;
        });
    },
    downloadTrustedAnchor(hash: string) {
      return axios
        .get(`/trusted-anchors/${hash}/download`, {
          responseType: 'blob',
        })
        .then((resp) => {
          saveResponseAsFile(resp, 'trusted-anchor.xml');
        })
        .catch((error) => {
          throw error;
        });
    },
    deleteTrustedAnchor(hash: string) {
      return axios
        .delete(`/trusted-anchors/${hash}`, {
          responseType: 'blob',
        })
        .catch((error) => {
          throw error;
        });
    },
  },
});
