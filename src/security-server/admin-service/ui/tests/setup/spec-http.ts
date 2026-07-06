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
 * Spec-bound MSW http wrapper.
 *
 * `specHttp` is created with `createOpenApiHttp<paths>()` so every handler path,
 * HTTP method, and response status is checked against the OpenAPI spec at
 * TYPE-CHECK time — an unknown path or a status the spec does not define fails
 * `tsc`, not the test.
 *
 * `validateBody(schema, body)` runs AJV against an inline JSON Schema object
 * (derived from the spec's `components/schemas` section) at RUNTIME, so a
 * fixture body that drifts from the contract fails the test rather than silently
 * serving wrong data.
 */

import { createOpenApiHttp } from 'openapi-msw';
import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import type { paths } from '@/openapi-msw-paths';

const ajv = new Ajv({ allErrors: true, strict: false });
addFormats(ajv);

/**
 * Validates `body` against `schema` using AJV. Throws with a descriptive
 * message when the body does not conform, causing the test to fail immediately
 * rather than serving invalid fixture data.
 */
export function validateBody(schema: object, body: unknown): void {
  const valid = ajv.validate(schema, body);
  if (!valid) {
    const errors = ajv.errorsText(ajv.errors);
    throw new Error(`Fixture body failed schema validation:\n${errors}`);
  }
}

export const specHttp = createOpenApiHttp<paths>({ baseUrl: '/api/v1' });
