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
import {
  ConfigurationAnchor,
  ConfigurationAnchorContainer,
  ConfigurationPart,
  ConfigurationType,
  GlobalConfDownloadUrl,
} from '@/openapi-types';
import {saveResponseAsFile} from '@niis/shared-ui';
import axios from 'axios';
import {defineStore} from 'pinia';

type KeyedConfigurationPart = ConfigurationPart & { key: string };

export interface State {
  internal: Source;
  external: Source;
}

export interface Source {
  downloadUrl: GlobalConfDownloadUrl;
  anchor: ConfigurationAnchor;
  parts: KeyedConfigurationPart[];
}

function baseUrl(configurationType: ConfigurationType, ...path: (string | number)[]) {
  return `/configuration-sources/${configurationType}/${path.join('/')}`;
}

export const useConfigurationSource = defineStore('configurationSource', {
  state: (): State => ({
    internal: {
      downloadUrl: {} as GlobalConfDownloadUrl,
      anchor: {} as ConfigurationAnchor,
      parts: [],
    },
    external: {
      downloadUrl: {} as GlobalConfDownloadUrl,
      anchor: {} as ConfigurationAnchor,
      parts: [],
    },
  }),

  actions: {
    getSource(configurationType: ConfigurationType): Source {
      return ConfigurationType.INTERNAL == configurationType ? this.internal : this.external;
    },
    getDownloadUrl(configurationType: ConfigurationType): GlobalConfDownloadUrl {
      return this.getSource(configurationType).downloadUrl;
    },
    async fetchDownloadUrl(configurationType: ConfigurationType) {
      return axios
        .get<GlobalConfDownloadUrl>(baseUrl(configurationType, 'download-url'))
        .then((resp) => (this.getSource(configurationType).downloadUrl = resp.data));
    },
    getConfigurationParts(configurationType: ConfigurationType): KeyedConfigurationPart[] {
      return this.getSource(configurationType).parts;
    },
    async fetchConfigurationParts(configurationType: ConfigurationType) {
      return axios
        .get<ConfigurationPart[]>(baseUrl(configurationType, 'configuration-parts'))
        .then((resp) => {
          this.getSource(configurationType).parts = resp.data
            .map(item => ({
              ...item,
              key: item.content_identifier + item.version
            }));
        });
    },
    async downloadConfigurationPartDownloadUrl(configurationType: ConfigurationType, contentIdentifier: string, version: number) {
      return axios
        .get(baseUrl(configurationType, 'configuration-parts', contentIdentifier, version, 'download'), {
          responseType: 'blob',
        })
        .then((resp) => saveResponseAsFile(resp));
    },
    uploadConfigurationFile(configurationType: ConfigurationType, contentIdentifier: string, partFile: File) {
      const formData = new FormData();
      formData.append('content_identifier', contentIdentifier);
      formData.append('file', partFile);
      return axios.post(baseUrl(configurationType, 'configuration-parts'), formData);
    },
    getAnchor(configurationType: ConfigurationType): ConfigurationAnchor {
      return this.getSource(configurationType).anchor;
    },
    hasAnchor(configurationType: ConfigurationType): boolean {
      return this.getSource(configurationType).anchor?.hash != undefined;
    },
    async fetchConfigurationAnchor(configurationType: ConfigurationType) {
      return axios
        .get<ConfigurationAnchorContainer>(baseUrl(configurationType, 'anchor'))
        .then((resp) => {
          if (resp.data.anchor) {
            this.getSource(configurationType).anchor = resp.data.anchor;
          }
        });
    },
    async downloadConfigurationAnchor(configurationType: ConfigurationType) {
      return axios
        .get<File>(baseUrl(configurationType, 'anchor', 'download'), {
          responseType: 'blob',
        })
        .then((resp) => saveResponseAsFile(resp));
    },
    async recreateConfigurationAnchor(configurationType: ConfigurationType) {
      return axios
        .put<ConfigurationAnchor>(baseUrl(configurationType, 'anchor', 're-create'), {})
        .then((resp) => (this.getSource(configurationType).anchor = resp.data));
    },
  },
});
