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
 * Shared AJV JSON Schema definitions derived from the CS OpenAPI components/schemas section.
 * Import these instead of re-declaring them in each spec file.
 */

const clientIdSchema = {
  type: 'object',
  required: ['instance_id', 'member_class', 'member_code', 'type'],
  properties: {
    instance_id: { type: 'string' },
    member_class: { type: 'string' },
    member_code: { type: 'string' },
    subsystem_code: { type: 'string' },
    encoded_id: { type: 'string' },
    type: { type: 'string' },
  },
};

export const clientSchema = {
  type: 'object',
  required: ['client_id'],
  properties: {
    member_name: { type: 'string' },
    client_id: clientIdSchema,
  },
};

export const pagedClientsSchema = {
  type: 'object',
  required: ['paging_metadata'],
  properties: {
    clients: { type: 'array', items: clientSchema },
    paging_metadata: {
      type: 'object',
      required: ['total_items', 'items', 'limit', 'offset'],
      properties: {
        total_items: { type: 'integer' },
        items: { type: 'integer' },
        limit: { type: 'integer' },
        offset: { type: 'integer' },
      },
    },
  },
};

export const sessionStatusSchema = {
  type: 'object',
  required: ['valid'],
  properties: {
    valid: { type: 'boolean' },
  },
};
