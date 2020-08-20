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
import dtsGenerator, {DefaultTypeNameConvertor} from 'dtsgenerator';
import * as fs from 'fs';
import {parseFileContent} from 'dtsgenerator/dist/utils';
import prettier from 'prettier';

const openAPIPath = '../src/main/resources/openapi-definition.yaml';
const outputTSPath = 'src/openapi-types.ts';

function main() {
  console.log(`Generating typescript interfaces from ${openAPIPath}`);
  dtsGenerator({
    contents: [parseFileContent(fs.readFileSync(openAPIPath, 'utf-8'), openAPIPath)],
    typeNameConvertor: id => {
      const names = DefaultTypeNameConvertor(id);
      return names.some(name => name.toLowerCase() === 'schemas') ? [names[names.length - 1]] : [];
    }
  })
    .then(result => {
      result = result
        .split("\n")
        .map(line => line.startsWith('declare') ? line.replace('declare', 'export') : line)
        .join("\n");
      prettier.resolveConfig(process.cwd())
        .then(options => {
          fs.writeFileSync(outputTSPath, prettier.format(result, {
            ...options,
            parser: 'typescript'
          }), {encoding: 'utf-8'});
          console.log(`Wrote typescript interfaces to ${outputTSPath}`);
        }).catch((err: any) => console.error(err.stack || err));
    }).catch((err: any) => console.error(err.stack || err));
}

main();
