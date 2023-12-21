/*
 * The MIT License
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

import { Key, Token, TokenCertificate, TokenPinUpdate } from '@/openapi-types';
import * as api from '@/util/api';
import { deepClone } from '@/util/helpers';
import { encodePathParameter } from '@/util/api';
import { useAlerts } from './alerts';
import { defineStore } from 'pinia';

function sortTokens(tokens: Token[]): Token[] {
  // Sort array by id:s so it doesn't jump around. Order of items in the backend reply changes between requests.
  const arr = deepClone(tokens).sort((a, b) => {
    if (a.id < b.id) {
      return -1;
    }
    if (a.id > b.id) {
      return 1;
    }

    // equal id:s. (should not happen)
    return 0;
  });

  return arr;
}

export const useTokens = defineStore('tokens', {
  state: () => {
    return {
      expandedTokens: [] as string[],
      tokens: [] as Token[],
      selectedToken: undefined as Token | undefined,
    };
  },
  persist: {
    paths: ['tokens', 'selectedToken'],
  },
  getters: {
    filteredTokens: (state) => (search: string) => {
      // Filter term is applied to token name key name and certificate owner id
      let arr = deepClone<Token[]>(sortTokens(state.tokens));

      if (!search) {
        return arr;
      }

      const mysearch = search.toLowerCase();

      if (mysearch.length < 1) {
        return state.tokens;
      }

      arr.forEach((token) => {
        token.keys.forEach((key: Key) => {
          const certs = key.certificates.filter((cert: TokenCertificate) => {
            if (cert.owner_id) {
              return cert.owner_id.toLowerCase().includes(mysearch);
            }
            return false;
          });
          key.certificates = certs;
        });
      });

      arr.forEach((token: Token) => {
        const keys = token.keys.filter((key: Key) => {
          if (key?.certificates?.length > 0) {
            return true;
          }

          if (key.name) {
            return key.name.toLowerCase().includes(mysearch);
          }
          return false;
        });
        token.keys = keys;
      });

      arr = arr.filter((token: Token) => {
        if (token?.keys?.length > 0) {
          return true;
        }

        return token.name.toLowerCase().includes(mysearch);
      });

      return arr;
    },

    sortedTokens(state): Token[] {
      if (!state.tokens || state.tokens.length === 0) {
        return [];
      }

      return sortTokens(state.tokens);
    },

    tokenExpanded: (state) => (id: string) => {
      return state.expandedTokens.includes(id);
    },

    tokensFilteredByName:
      (state) =>
      (search: string | undefined): Token[] => {
        // Filter term is applied to token name
        const arr: Token[] = sortTokens(state.tokens);

        if (!search || search.length < 1) {
          return arr;
        }

        const mysearch = search.toLowerCase();

        return arr.filter((token: Token) => {
          return token.name.toLowerCase().includes(mysearch);
        });
      },
  },

  actions: {
    expandToken(id: string) {
      const index = this.expandedTokens.findIndex((element) => {
        return element === id;
      });

      if (index === -1) {
        this.expandedTokens.push(id);
      }
    },

    fetchTokens() {
      // Fetch tokens from backend
      return api
        .get<Token[]>('/tokens')
        .then((res) => {
          this.tokens = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    hideToken(id: string) {
      const index = this.expandedTokens.findIndex((element) => {
        return element === id;
      });

      if (index >= 0) {
        this.expandedTokens.splice(index, 1);
      }
    },

    setSelectedToken(token: Token) {
      this.selectedToken = token;
    },

    tokenLogout(id: string) {
      return api
        .put(`/tokens/${encodePathParameter(id)}/logout`, {})
        .then(() => {
          // Update tokens
          this.fetchTokens();
          const alerts = useAlerts();
          alerts.checkAlertStatus();
        })
        .catch((error) => {
          throw error;
        });
    },
    updatePin(tokenId: string, oldPin: string, newPin: string) {
      const tokenPinUpdate: TokenPinUpdate = {
        old_pin: oldPin,
        new_pin: newPin,
      };
      return api
        .put(`/tokens/${encodePathParameter(tokenId)}/pin`, tokenPinUpdate)
        .catch((error) => {
          throw error;
        });
    },
    updateToken(token: Token) {
      return api
        .patch<Token>(`/tokens/${encodePathParameter(token.id)}`, token)
        .then((res) => {
          const tokenIndex = this.tokens.findIndex((t) => t.id === token.id);
          this.tokens[tokenIndex] = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },
  },
});
