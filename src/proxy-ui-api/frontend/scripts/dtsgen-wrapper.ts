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
