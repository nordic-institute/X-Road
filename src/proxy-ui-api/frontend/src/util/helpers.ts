
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

// Save response data as a file
export function saveResponseAsFile(response: any) {
  let suggestedFileName;
  const disposition = response.headers['content-disposition'];

  if (disposition && disposition.indexOf('attachment') !== -1) {
    const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
    const matches = filenameRegex.exec(disposition);
    if (matches != null && matches[1]) {
      suggestedFileName = matches[1].replace(/['"]/g, '');
    }
  }

  const effectiveFileName =
    suggestedFileName === undefined
      ? 'certs.tar.gz'
      : suggestedFileName;
  const blob = new Blob([response.data]);

  // Create a link to DOM and click it. This will trigger the browser to start file download.
  const link = document.createElement('a');
  link.href = window.URL.createObjectURL(blob);
  link.setAttribute('download', effectiveFileName);
  document.body.appendChild(link);
  link.click();
}

