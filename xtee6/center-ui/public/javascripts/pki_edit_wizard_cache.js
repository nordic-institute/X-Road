var pkiEditWizardCache = function() {
    var nameExtractorCached = false;
    var topCaOcspInfosCached = false;
    var intermediateCasCached = false;

    function cacheNameExtractor() {
        nameExtractorCached = true;
    }

    function isNameExtractorCached() {
        return nameExtractorCached;
    }

    function cacheTopCaOcspInfos() {
        topCaOcspInfosCached = true;
    }

    function areTopCaOcspInfosCached() {
        return topCaOcspInfosCached;
    }

    function cacheIntmermediateCas() {
        intermediateCasCached = true;
    }

    function areIntermediateCasCached() {
        return intermediateCasCached;
    }

    function clear() {
        nameExtractorCached = false;
        topCaOcspInfosCached = false;
        intermediateCasCached = false;
    }

    return {
        cacheNameExtractor: cacheNameExtractor,
        isNameExtractorCached: isNameExtractorCached,

        cacheTopCaOcspInfos: cacheTopCaOcspInfos,
        areTopCaOcspInfosCached: areTopCaOcspInfosCached,

        cacheIntmermediateCas: cacheIntmermediateCas,
        areIntermediateCasCached: areIntermediateCasCached,

        clear: clear
    }
}();
