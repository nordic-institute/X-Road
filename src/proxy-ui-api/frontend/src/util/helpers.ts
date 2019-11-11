
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

// Checks if the given WSDL URL valid
export function isValidWsdlURL(str: string) {
  const pattern = new RegExp('(^(https?):\/\/\/?)[-a-zA-Z0-9]');
  return !!pattern.test(str);
}

// Checks if the given REST URL is valid
export function isValidRestURL(str: string) {
  return isValidWsdlURL(str);
}

