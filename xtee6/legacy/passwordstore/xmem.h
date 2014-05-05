#ifndef __xmem_h
#define __xmem_h

#ifdef __cplusplus
extern "C"{
#endif

/**
    read-write lock semafooride grupi baasil jagatud malu segmendi jaoks
    ====================================================================

    liides kasutajaprogrammidega
    ----------------------------

    yldist: koik funktsioonid tagastava 0, kui operatsioon onnestus ning
    -1 kui mitte. xm->error seatakse sel juhul. funktsioonid ei blokeeru,
    juhul kui pole teisti oeldud. kui protsess lopetab too enneaegselt 
    vabastatakse koik temaga seotud lukud automaatselt.
*/

/**
    kasutaja ei vaja siit seest yhtki valja. 
*/
struct xmem{
    int semid;    /* semafoori id */
    int pshmid;    /* viitesegmendi id */
    int dshmid;    /* andmesegmendi id */
    void* pshmptr;    /* viitesegmendi viit */
    void* dshmptr;    /* andmesegmendi viit */
    int error;    /* vaata seda paris errno asemel */
    int rl_count;    /* mitu readlocki see protsess/thread ise on jarjest teinud */
    int wl_count;    /* mitu writelocki see protsess/thread ise on jarjest teinud */
    int perms; /* Kasutaja poolt avamisel ette antud permissioonid. */
};

/**
    initsialiseerib objekti. kui semafoor ning jagatud malu segment puudusid, siis
    loob ka need ning initsialiseerib. kui viga ei olnud annab tagasi 0, muidu -1 ja
    seab errori. see funktsioon voib blokeeruda kui kaks protsessi yritavad tapselt
    samal ajal semafoori ja segmenti luua. 
    Parameeter perms sisaldab permissioone.
*/
int xmem_open(struct xmem* xm, key_t key_sem, key_t key_mem, int perms);

/**
    havitab objekti. kui tegu oli viimase viitega antud semaforile siis havitab 
    ka semafori ja jagatud malu segmendi. 
*/
int xmem_close(struct xmem* xm);

/**
    laseb objekti lahti. erinevalt xmem_close funktsioonist jaab objekt jagatud
    mallu alles ka siis, kui ykski protsess seda enam ei kasuta
*/
int xmem_detach(struct xmem* xm);

/**
    muudab jagatud malu segmendi suurust. algul on suurus 0. enne funktsiooni taitmist
    tuleb saada write lock. vana segmendi sisu ei kopeerita
*/
int xmem_resize(struct xmem* xm, size_t size);

/**
    muudab jagatud malu segmendi suurust ja kopeerib vana segmendi sisu uude. 
    algul on suurus 0. enne funktsiooni taitmist tuleb saada write lock. 
*/
int xmem_resize_and_copy(struct xmem* xm, size_t size);

/**
    lukustab segmendi lugemiseks. funktsioon voib blokeeruda. juhul kui samal protsessil
    on juba kirjutamislukk annab tagasi -1 ja error on EWOULDBLOCK.
*/
int xmem_readlock(struct xmem* xm);

/**
    yritab segmenti lukustada lugemiseks. juhul kui samal protsessil
    on juba kirjutamislukk annab tagasi -1 ja error on EWOULDBLOCK.
*/
int xmem_tryreadlock(struct xmem* xm);

/**
    lukustab segmendi kirjutamiseks. funktsioon voib blokeeruda. juhul kui samal 
    protsessil on juba lugemislukk annab tagasi -1 ja error on EWOULDBLOCK.
*/
int xmem_writelock(struct xmem* xm);

/**
    yritab segmenti lukustada kirjutamiseks. juhul kui samal 
    protsessil on juba lugemislukk annab tagasi -1 ja error on EWOULDBLOCK.
*/
int xmem_trywritelock(struct xmem* xm);

/**
    vabastab yhe luku. kui lukustamisfunktsioone on kasutatud mitu korda jarjest
    tuleb ka avamisfunktsiooni mitu korda jarjest pruukida.
*/
int xmem_unlock(struct xmem* xm);

/**
    tagastab jagatud malu segmendi pikkuse. enne selle funktsiooni kasutamist 
    tuleb votta mingi lukk. vea korral tagastab 0.
*/
size_t xmem_len(struct xmem* xm);

/**
    tagastab pointeri jagatud malusegmendi algusele. enne selle funktsiooni
    kasutamist tuleb votta mingi lukk. vea korral tagastab 0.
*/
void* xmem_ptr(struct xmem* xm);

#ifdef __cplusplus
}
#endif

#endif

