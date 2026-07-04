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

/**
 * Reports an error when an object literal is passed as the body argument (2nd positional arg)
 * to api.post / api.put / api.patch (and bare post / put / patch re-exports).
 * Forces callers to extract the body to a named const typed via the operation's generated *Data['body'] type.
 */
export const noInlineApiBody = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Disallow inline object literals as request body in api.post/put/patch calls',
    },
    schema: [],
    messages: {
      noInlineBody:
        'Pass a named const typed via the operation\'s generated *Data[\'body\'] type instead of an inline object literal.',
    },
  },
  create(context) {
    const MUTATION_METHODS = new Set(['post', 'put', 'patch']);

    function isApiMutationCall(node) {
      if (node.type !== 'CallExpression') return false;
      const { callee } = node;

      // api.post / api.put / api.patch
      if (
        callee.type === 'MemberExpression' &&
        !callee.computed &&
        callee.property.type === 'Identifier' &&
        MUTATION_METHODS.has(callee.property.name)
      ) {
        return true;
      }

      // bare post / put / patch (imported directly)
      if (callee.type === 'Identifier' && MUTATION_METHODS.has(callee.name)) {
        return true;
      }

      return false;
    }

    return {
      CallExpression(node) {
        if (!isApiMutationCall(node)) return;
        const args = node.arguments;
        // body is the 2nd positional argument (index 1)
        const bodyArg = args[1];
        if (bodyArg && bodyArg.type === 'ObjectExpression') {
          context.report({ node: bodyArg, messageId: 'noInlineBody' });
        }
      },
    };
  },
};
