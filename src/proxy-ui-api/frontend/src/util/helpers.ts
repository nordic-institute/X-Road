
// Helps with datatable filter function.
// Copied from: https://github.com/vuetifyjs/vuetify/blob/master/packages/vuetify/src/util/helpers.ts
export function getNestedValue(obj: any, path: Array<string | number>, fallback?: any): any {
  const last = path.length - 1;

  if (last < 0) { return obj === undefined ? fallback : obj; }

  for (let i = 0; i < last; i++) {
    if (obj == null) {
      return fallback;
    }
    obj = obj[path[i]];
  }

  if (obj == null) { return fallback; }

  return obj[path[last]] === undefined ? fallback : obj[path[last]];
}

// Helps with datatable filter function.
// Copied from: https://github.com/vuetifyjs/vuetify/blob/master/packages/vuetify/src/util/helpers.ts
export function getObjectValueByPath(obj: object, path: string, fallback?: any): any {
  // credit: http://stackoverflow.com/questions/6491463/accessing-nested-javascript-objects-with-string-key#comment55278413_6491621
  if (!path || path.constructor !== String) { return fallback; }
  path = path.replace(/\[(\w+)\]/g, '.$1'); // convert indexes to properties
  path = path.replace(/^\./, ''); // strip a leading dot
  return getNestedValue(obj, path.split('.'), fallback);
}

// Filters an array of objects excluding specified object key
export function selectedFilter(arr: any[], search: string, excluded?: string): any[] {


  // Clean the search string
  const mysearch = search.toString().toLowerCase();
  if (mysearch.trim() === '') {
    return arr;
  }

  const filtered = arr.filter((g: any) => {

    let filteredKeys = Object.keys(g);

    // If there is an excluded key remove it from the keys
    if (excluded) {
      filteredKeys = filteredKeys.filter((value) => {
        return value !== 'id';
      });
    }

    return filteredKeys.find((key: string) => {
      return g[key]
        .toString()
        .toLowerCase()
        .includes(mysearch);
    });
  });

  return filtered;
}

// Checks if the given url is valid
export function isValidURL(str: string) {
  const pattern = new RegExp('^(https?:\\/\\/)?' + // protocol
    '((([a-z\\d]([a-z\\d-]*[a-z\\d])*)\\.)+[a-z]{2,}|' + // domain name
    '((\\d{1,3}\\.){3}\\d{1,3}))' + // OR ip (v4) address
    '(\\:\\d+)?(\\/[-a-z\\d%_.~+]*)*' + // port and path
    '(\\?[;&a-z\\d%_.~+=-]*)?' + // query string
    '(\\#[-a-z\\d_]*)?$', 'i'); // fragment locator
  return !!pattern.test(str);
}


// Checks if the given WSDL URL valid
export function isValidWsdlURL(str: string) {
  const pattern = new RegExp('^((https?|ftp|file):\\/\\/\\/?)?' + // protocol
    '((([a-z\\d]([a-z\\d-]*[a-z\\d])*)\\.)+[a-z]{2,}|' + // domain name
    '((\\d{1,3}\\.){3}\\d{1,3}))' + // OR ip (v4) address
    '(\\:\\d+)?(\\/[-a-z\\d%_.~+]*)*' + // port and path
    '(\\?[;&a-z\\d%_.~+=-]*)?' + // query string
    '(\\#[-a-z\\d_]*)?$', 'i'); // fragment locator
  return !!pattern.test(str);
}


// Checks if the given REST URL is valid
export function isValidRestURL(str: string) {
  const pattern = new RegExp('^(https?:\\/\\/)?' + // protocol
    '((([a-z\\d]([a-z\\d-]*[a-z\\d])*)\\.)+[a-z]{2,}|' + // domain name
    '((\\d{1,3}\\.){3}\\d{1,3}))' + // OR ip (v4) address
    '(\\:\\d+)?(\\/[-a-z\\d%_.~+]*)*' + // port and path
    '(\\?[;&a-z\\d%_.~+=-]*)?' + // query string
    '(\\#[-a-z\\d_]*)?$', 'i'); // fragment locator
  return !!pattern.test(str);
}

