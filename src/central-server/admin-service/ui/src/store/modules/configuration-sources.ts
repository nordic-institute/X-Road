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
import { saveResponseAsFile } from '@/util/helpers';
import axios from 'axios';
import { defineStore } from 'pinia';

export interface State {
  internal: Source;
  external: Source;
}

export interface Source {
  downloadUrl: GlobalConfDownloadUrl;
  anchor: ConfigurationAnchor;
  parts: ConfigurationPart[];
}

export const useConfigurationSourceStore = defineStore('configurationSource', {
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
      return ConfigurationType.INTERNAL == configurationType
        ? this.internal
        : this.external;
    },
    getDownloadUrl(
      configurationType: ConfigurationType,
    ): GlobalConfDownloadUrl {
      return this.getSource(configurationType).downloadUrl;
    },
    fetchDownloadUrl(configurationType: ConfigurationType) {
      return axios
        .get<GlobalConfDownloadUrl>(
          `/configuration-sources/${configurationType}/download-url`,
        )
        .then((resp) => {
          this.getSource(configurationType).downloadUrl = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    getConfigurationParts(
      configurationType: ConfigurationType,
    ): ConfigurationPart[] {
      return this.getSource(configurationType).parts;
    },
    fetchConfigurationParts(configurationType: ConfigurationType) {
      return axios
        .get<ConfigurationPart[]>(
          `/configuration-sources/${configurationType}/configuration-parts`,
        )
        .then((resp) => {
          this.getSource(configurationType).parts = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    downloadConfigurationPartDownloadUrl(
      configurationType: ConfigurationType,
      contentIdentifier: string,
      version: number,
    ) {
      return axios
        .get(
          `/configuration-sources/${configurationType}/configuration-parts/${contentIdentifier}/${version}/download`,
          { responseType: 'blob' },
        )
        .then((resp) => {
          saveResponseAsFile(resp);
        })
        .catch((error) => {
          throw error;
        });
    },
    uploadConfigurationFile(
      configurationType: ConfigurationType,
      contentIdentifier: string,
      partFile: File,
    ) {
      const formData = new FormData();
      formData.append('content_identifier', contentIdentifier);
      formData.append('file', partFile);
      return axios.post(
        `/configuration-sources/${configurationType}/configuration-parts`,
        formData,
      );
    },
    getAnchor(configurationType: ConfigurationType): ConfigurationAnchor {
      return this.getSource(configurationType).anchor;
    },
    hasAnchor(configurationType: ConfigurationType): boolean {
      return this.getSource(configurationType).anchor?.hash != undefined;
    },
    fetchConfigurationAnchor(configurationType: ConfigurationType) {
      return axios
        .get<ConfigurationAnchorContainer>(
          `/configuration-sources/${configurationType}/anchor`,
        )
        .then((resp) => {
          if (resp.data.anchor) {
            this.getSource(configurationType).anchor = resp.data.anchor;
          }
        })
        .catch((error) => {
          throw error;
        });
    },
    downloadConfigurationAnchor(configurationType: ConfigurationType) {
      return axios
        .get<File>(
          `/configuration-sources/${configurationType}/anchor/download`,
          {
            responseType: 'blob',
          },
        )
        .then((resp) => {
          saveResponseAsFile(resp);
        })
        .catch((error) => {
          throw error;
        });
    },
    recreateConfigurationAnchor(configurationType: ConfigurationType) {
      return axios
        .put<ConfigurationAnchor>(
          `/configuration-sources/${configurationType}/anchor/re-create`,
        )
        .then((resp) => {
          this.getSource(configurationType).anchor = resp.data;
        })
        .catch((error) => {
          throw error;
        });
    },
  },
});
