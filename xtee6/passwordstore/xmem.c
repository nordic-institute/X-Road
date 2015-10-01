#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/sem.h>
#include <sys/shm.h>
#include <errno.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>

#include "xmem.h"

#if defined(__GNU_LIBRARY__) && !defined(_SEM_SEMUN_UNDEFINED)
/* union semun is defined by including <sys/sem.h> */
#else
/* according to X/OPEN we have to define it ourselves */
union semun {
  int val;            /* value for SETVAL */
  struct semid_ds *buf;        /* buffer for IPC_STAT, IPC_SET */
  unsigned short int *array;    /* array for GETALL, SETALL */
  struct seminfo *__buf;    /* buffer for IPC_INFO */
};
#endif

/*
    koigile semafooridele antakse algvaartuseks mingi suur arv (100) naiteks.
    semafoorid ise on jargmised:
    
    lockcount - lukkude arv. iga lugeja votab yhe luku ning iga kirjutaja 
    100 lukku. 

    attached - mitu protsessi on antud objekti kylge liitunud.
    
*/

#define MAXLOCKS 100

static int solidsemop (int __semid, struct sembuf *__sops, unsigned __nsops)
{
    int err;

    while((err= semop(__semid, __sops, __nsops)) == -1 && errno == EINTR){
        /* kui see oli signaal siis laseme ikka edasi */
    }
    return(err);
}

int xmem_open(struct xmem* xm, key_t key_sem, key_t key_mem, int perms)
{
    int retval= -1;

    /* initsialiseerime kontektsistruktuuri */
    xm->semid= -1;
    xm->pshmid= -1;
    xm->dshmid= -1;
    xm->pshmptr= 0;
    xm->dshmptr= 0;
    xm->rl_count= 0;
    xm->wl_count= 0;
    xm->error= 0;
    xm->perms = perms;

    /* loome semafoori. linux (vahemalt kikus) ei defineeri SEM_R ja SEM_A
    konstante. seeparast olin ma oi kui laisk ja kasutasin SHM omi */
    if((xm->semid= semget(key_sem, 2, IPC_CREAT | IPC_EXCL | SHM_R | SHM_W
            | perms)) >= 0){
        /* onnestus -- seega olime esimesed! */
        /* loome jagatud malu segmendi pointeri ja suuruse jaoks. siin on
        lood nii, et juhul kui loomine ei onnestu proovime lihtsalt ligi
        votta... */
        if((xm->pshmid= shmget(key_mem, sizeof(int)+ sizeof(size_t), 
                IPC_CREAT | IPC_EXCL | SHM_R | SHM_W | perms)) >= 0 ||
                (xm->pshmid= shmget(key_mem, 0, 0)) >= 0){
            /* proovime mitteswapitavaks teha */
            if (shmctl(xm->pshmid, SHM_LOCK, NULL) != 0) {
                //LOG_log(LOG_WARNING, "shmctl(SHM_LOCK) failed with %d", errno);
            }
            /* haagime selle segmendi kylge */
            if((xm->pshmptr= shmat(xm->pshmid, 0, 0)) != (void *)-1){
                unsigned short val[2]= {MAXLOCKS, MAXLOCKS};
                union semun arg;

                /* initsialiseerime viidasegmendi */
                *((int *)xm->pshmptr)= -1;
                *((size_t *)((char *)xm->pshmptr+ sizeof(int)))= 0;

                /* nyyd initsialiseerime semafoori nii et 
                yhtki lukku ega yhendust pole */
                arg.array= val;
                if(semctl(xm->semid, 0, SETALL, arg) >= 0){
                    /* ja siis paneme kirja yhe attachmendi */
                    struct sembuf ops[1];

                    ops[0].sem_num= 1;
                    ops[0].sem_op= -1;
                    ops[0].sem_flg= SEM_UNDO;
                    if(solidsemop(xm->semid, ops, 1) == 0){
                        /* koik on korras: valmis on tyhi pointerisegment
                        ja semafoor on ilusti initsialiseeritud */
                        retval= 0;
                    }else{
                        xm->error= errno;
                    }
                }else{
                    xm->error= errno;
                }
            }else{
                xm->pshmptr= 0;
                xm->error= errno;
            }
        }else{
            xm->error= errno;
        }
        if(retval == -1){
            /* kuskil laks miski vussi. kuna tegu on uue jagatud objekti 
            loomisega peame havitama ka semafoori ning jagatud malu segmendi */
            if(xm->pshmptr != 0){
                shmdt(xm->pshmptr);
                xm->pshmptr= 0;
            }
            if(xm->pshmid != -1){
                shmctl(xm->pshmid, IPC_RMID, 0);
                xm->pshmid= -1;
            }
            if(xm->semid != -1){
                union semun arg;

                semctl(xm->semid, 0, IPC_RMID, arg);
                xm->semid= -1;
            }
        }
    }else if((xm->semid= semget(key_sem, 0, 0)) >= 0){
        /* selline semafoor oli juba olemas */
        /* kontrollime kas semafoor on juba initsialiseeritud */
        struct semid_ds semstate;
        union semun arg;
        int err;

        arg.buf= &semstate;
        while((err= semctl(xm->semid, 0, IPC_STAT, arg)) >= 0 && semstate.sem_otime == 0){
            /* lubatud voimalik blokeerumine */
            sleep(1);
        }
        if(err >= 0){
            /* semafor on nyyd olemas. votame write locki, liitume maluga, margime end
            attachituks ja anname locki tagasi. */
            struct sembuf ops[1];

            ops[0].sem_num= 0;
            ops[0].sem_op= -MAXLOCKS;
            ops[0].sem_flg= SEM_UNDO;
            if(solidsemop(xm->semid, ops, 1) == 0){
                /* kirjutuslukk on meie. votame jagatud malu segmendi kah kylge */
                if((xm->pshmid= shmget(key_mem, 0, 0)) >= 0){
                    /* haagime selle segmendi kylge */
                    if((xm->pshmptr= shmat(xm->pshmid, 0, 0)) != (void *)-1){
                        /* suurendame attachide arvu */
                        ops[0].sem_num= 1;
                        ops[0].sem_op= -1;
                        ops[0].sem_flg= SEM_UNDO;
                        if(solidsemop(xm->semid, ops, 1) == 0){
                            /* korras. */
                            retval= 0;
                        }else{
                            xm->error= errno;
                        }
                    }else{
                        xm->pshmptr= 0;
                        xm->error= errno;
                    }
                }else{
                    xm->error= errno;
                }
                /* anname nyyd kirjutamisluku vabaks jalle... */
                ops[0].sem_num= 0;
                ops[0].sem_op= MAXLOCKS;
                ops[0].sem_flg= SEM_UNDO;
                if(solidsemop(xm->semid, ops, 1) != 0){
                    /* no siin ei oska ma miskit arvata. see on
                    tegelikult paris jama sest lukk jaab peale.
                    samas ei nae pohjust miks nii juhtuma peaks */
                    retval= -1;
                    xm->error= errno;
                }
            }else{
                xm->error= errno;
            }
        }else{
            xm->error= errno;
        }
        if(retval == -1){
            xm->semid= -1;
            xm->pshmid= -1;
            if(xm->pshmptr != 0){
                shmdt(xm->pshmptr);
                xm->pshmptr= 0;
            }
        }
    }else{
        /* mingi suurem jama */
        xm->error= errno;
    }

    return(retval);
}

int xmem_close(struct xmem* xm)
{
    int retval= -1;

    xm->error= 0;

    if(xm->semid != -1){
        /* nyyd votame write locki ja vaatame kas me oleme viimased minejad... 
        kuna keegi hull voib kutsuda selle funktsiooni valja ka siis kui tal on
        lock, siis: read locki korral votame veel MAXLOCKS- 1 lukku juurde. ja 
        kirjutamisluku korral laseme kohe vabalt edasi. */
        struct sembuf ops[2];
        union semun arg;

        ops[0].sem_num= 0;
        ops[0].sem_op= -(MAXLOCKS- (xm->rl_count > 0));
        ops[0].sem_flg= SEM_UNDO;
        if(xm->wl_count > 0 || solidsemop(xm->semid, ops, 1) == 0){
            /* vaatame, mitu asjapulka on ennast attachinud */
            if(semctl(xm->semid, 1, GETVAL, arg) == MAXLOCKS- 1){
                /* ongi nii: kustutame tuled */
                /* jooksva andmesegmendi voti */
                int current_dshmid= *((int *)xm->pshmptr);

                /* laseme selle segmendi minna */
                if(current_dshmid != -1){
                    shmctl(current_dshmid, IPC_RMID, 0);
                }

                if(xm->pshmid != -1){
                    shmctl(xm->pshmid, IPC_RMID, 0);
                    xm->pshmid= -1;
                }
                if(xm->semid != -1){
                    semctl(xm->semid, 0, IPC_RMID, arg);
                    xm->semid= -1;
                }
                retval= 0;
            }else{
                /* ei olnud. vahendame siis attachide arvu ja
                anname write locki ka ara */
                ops[0].sem_num= 1;
                ops[0].sem_op= 1;
                ops[0].sem_flg= SEM_UNDO;
                ops[1].sem_num= 0;
                ops[1].sem_op= MAXLOCKS;
                ops[1].sem_flg= SEM_UNDO;

                if(solidsemop(xm->semid, ops, 2) == 0){
                    /* koik korras */
                    retval= 0;
                }else{
                    xm->error= errno;
                }
            }
        }else{
            /* kui me kirjutamislocki ei saanud, siis loodetavasti 
            oli see viga EIDRM. kes iganes havitas semafoori, pidi
            havitama ka jagatud malu segmendid */
            xm->error= errno;
        }

        /* ja lopuks vabastame segmendid */
        if(xm->pshmptr != 0){
            shmdt(xm->pshmptr);
            xm->pshmptr= 0;
        }
        if(xm->dshmptr != 0){
            shmdt(xm->dshmptr);
            xm->dshmptr= 0;
        }
    }else{
        /* kui semid == -1, siis ei saa ka ykski muu id olla midagi muud kui -1 */
        xm->error= EINVAL;
    }

    /* huvitav kas keegi seda kunagi kontrollib ka?? */
    return(retval);
}

int xmem_detach(struct xmem* xm)
{
    int retval= -1;

    xm->error= 0;

    if(xm->semid != -1){
        /* anname ara koik lukud, mis meil on ning vahendame ka attachide arvu */
        struct sembuf ops[2];
        int semcnt= 2;

        ops[0].sem_num= 1;
        ops[0].sem_op= 1;
        ops[0].sem_flg= SEM_UNDO;
        ops[1].sem_num= 0;
        ops[1].sem_op= (xm->wl_count > 0) ? MAXLOCKS : 
                ((xm->rl_count > 0) ? 1 : 0);
        ops[1].sem_flg= SEM_UNDO;

        if(ops[1].sem_op == 0){
            semcnt= 1;
        }
        if(solidsemop(xm->semid, ops, semcnt) == 0){
            /* koik korras */
            retval= 0;
        }else{
            xm->error= errno;
        }

        /* ja lopuks vabastame segmendid */
        if(xm->pshmptr != 0){
            shmdt(xm->pshmptr);
            xm->pshmptr= 0;
        }
        if(xm->dshmptr != 0){
            shmdt(xm->dshmptr);
            xm->dshmptr= 0;
        }
    }else{
        /* kui semid == -1, siis ei saa ka ykski muu id olla midagi muud kui -1 */
        xm->error= EINVAL;
    }

    /* huvitav kas keegi seda kunagi kontrollib ka?? */
    return(retval);
}

int xmem_resize(struct xmem* xm, size_t size)
{
    int retval= -1;

    xm->error= 0;

    /* segment peab olema kirjutamislukus ja viidasegment attachitud */
    if(xm->wl_count && xm->pshmptr){
        size_t current_size= *((size_t *)((char *)xm->pshmptr+ sizeof(int)));

        if(size == current_size){
            /* nae sellise suurusega segment oli juba olemas */
            retval= 0;
        }else{
            /* jooksva andmesegmendi voti */
            int current_dshmid= *((int *)xm->pshmptr);

            if(current_dshmid != -1){
                /* havitame selle segmendi ara kah */
                shmctl(current_dshmid, IPC_RMID, 0);
            }

            /* nullime molemad suurused (votme ja pikkuse) viidasegmendis */
            *((int *)xm->pshmptr)= -1;
            *((size_t *)((char *)xm->pshmptr+ sizeof(int)))= 0;

            if(size > 0){
                /* loome uue jagatud malu segmendi soovitud suurusega */
                current_dshmid= shmget(IPC_PRIVATE, size, 
                        IPC_CREAT | IPC_EXCL | SHM_R | SHM_W |
                        xm->perms);
                if(current_dshmid >= 0){
                    *((int *)xm->pshmptr)= current_dshmid;
                    *((size_t *)((char *)xm->pshmptr+ sizeof(int)))= size;
                    if (shmctl(current_dshmid, SHM_LOCK, NULL) != 0) {
                        //LOG_log(LOG_WARNING, "shmctl(SHM_LOCK) failed with %d", errno);
                    }
                    retval= 0;
                }else{
                    xm->error= errno;
                }
            }else{
                retval= 0;
            }
        }
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

int xmem_resize_and_copy(struct xmem* xm, size_t size)
{
    int retval= -1;

    xm->error= 0;

    /* segment peab olema kirjutamislukus ja viidasegment attachitud */
    if(xm->wl_count && xm->pshmptr){
        size_t current_size= *((size_t *)((char *)xm->pshmptr+ sizeof(int)));

        if(size == current_size){
            /* nae sellise suurusega segment oli juba olemas */
            retval= 0;
        }else{
            /* jooksva andmesegmendi voti */
            int current_dshmid= *((int *)xm->pshmptr);

            /* viit vanadele anmdetel */
            void* oldptr;

            /* viit uutele andmetele */
            void* newptr;

            /* nullime molemad suurused (votme ja pikkuse) viidasegmendis */
            *((int *)xm->pshmptr)= -1;
            *((size_t *)((char *)xm->pshmptr+ sizeof(int)))= 0;

            if(size > 0){
                /* loome uue jagatud malu segmendi soovitud suurusega */
                int new_dshmid= shmget(IPC_PRIVATE, size, 
                        IPC_CREAT | IPC_EXCL | SHM_R | SHM_W |
                        xm->perms);
                if(new_dshmid >= 0){
                    /* paneme andmed uue andmesegmendi kohta
                    ka viidasegmenti */
                    *((int *)xm->pshmptr)= new_dshmid;
                    *((size_t *)((char *)xm->pshmptr+ sizeof(int)))= size;
                    if (shmctl(new_dshmid, SHM_LOCK, NULL) != 0) {
                        //LOG_log(LOG_WARNING, "shmctl(SHM_LOCK) failed with %d", errno);
                    }
                    retval= 0;
                }else{
                    xm->error= errno;
                }
            }else{
                retval= 0;
            }
            /* vota viit uue segmendi algusele */
            newptr= xmem_ptr(xm);
            /* vota viit vana segmendi algusele */
            if(current_dshmid == -1 || (oldptr= shmat(current_dshmid, 0, 0)) 
                    == (void *)-1){
                oldptr= 0;
            }
            /* kui molemad on mittenullid, siis kopeeri andmed */
            if(newptr && oldptr && current_size){
                memcpy(newptr, oldptr, current_size);
            }

            /* laseme vanal segmendil minna */
            if(oldptr){
                shmdt(oldptr);
            }

            /* havitame vana segmendi ara, nii et detach ta ara havitaks */
            if(current_dshmid != -1){
                shmctl(current_dshmid, IPC_RMID, 0);
            }
        }
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

int xmem_readlock(struct xmem* xm)
{
    int retval= -1;
    struct sembuf ops[1];

    xm->error= 0;

    /* kui meil on kirjutamislukk, siis ei saa samal ajal lugemislukku votta */
    if(xm->wl_count > 0){
        xm->error= EWOULDBLOCK;
    /* yks lugemislukk juba on, suurendame loendurit */
    }else if(xm->rl_count > 0){
        xm->rl_count++;
        retval= 0;
    /* kui meil ikka on semafoor kenasti kyljes */
    }else if(xm->semid != -1){
        ops[0].sem_num= 0;
        ops[0].sem_op= -1;
        ops[0].sem_flg= SEM_UNDO;

        /* blokeeruv lugemislukk */
        if(solidsemop(xm->semid, ops, 1) == 0){
            retval= 0;
            xm->rl_count++;
        }else{
            xm->error= errno;
        }
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

int xmem_tryreadlock(struct xmem* xm)
{
    int retval= -1;
    struct sembuf ops[1];

    xm->error= 0;

    /* kui meil on kirjutamislukk, siis ei saa samal ajal lugemislukku votta */
    if(xm->wl_count > 0){
        xm->error= EWOULDBLOCK;
    /* yks lugemislukk juba on, suurendame loendurit */
    }else if(xm->rl_count > 0){
        xm->rl_count++;
        retval= 0;
    /* kui meil ikka on semafoor kenasti kyljes */
    }else if(xm->semid != -1){
        ops[0].sem_num= 0;
        ops[0].sem_op= -1;
        ops[0].sem_flg= SEM_UNDO | IPC_NOWAIT;

        /* mitteblokeeruv lugemislukk */
        if(solidsemop(xm->semid, ops, 1) == 0){
            retval= 0;
            xm->rl_count++;
        }else{
            xm->error= errno;
        }
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

int xmem_writelock(struct xmem* xm)
{
    int retval= -1;
    struct sembuf ops[1];

    xm->error= 0;

    /* kui meil on lugemislukk, siis ei saa samal ajal kirjutamislukku votta */
    if(xm->rl_count > 0){
        xm->error= EWOULDBLOCK;
    /* yks kirjutamislukk juba on, suurendame loendurit */
    }else if(xm->wl_count > 0){
        xm->wl_count++;
        retval= 0;
    /* kui meil ikka on semafoor kenasti kyljes */
    }else if(xm->semid != -1){
        ops[0].sem_num= 0;
        ops[0].sem_op= -MAXLOCKS;
        ops[0].sem_flg= SEM_UNDO;

        /* blokeeruv kirjutamislukk */
        if(solidsemop(xm->semid, ops, 1) == 0){
            retval= 0;
            xm->wl_count++;
        }else{
            xm->error= errno;
        }
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

int xmem_trywritelock(struct xmem* xm)
{
    int retval= -1;
    struct sembuf ops[1];

    xm->error= 0;

    /* kui meil on lugemislukk, siis ei saa samal ajal kirjutamislukku votta */
    if(xm->rl_count > 0){
        xm->error= EWOULDBLOCK;
    /* yks kirjutamislukk juba on, suurendame loendurit */
    }else if(xm->wl_count > 0){
        xm->wl_count++;
        retval= 0;
    /* kui meil ikka on semafoor kenasti kyljes */
    }else if(xm->semid != -1){
        ops[0].sem_num= 0;
        ops[0].sem_op= -MAXLOCKS;
        ops[0].sem_flg= SEM_UNDO | IPC_NOWAIT;

        /* mitteblokeeruv kirjutamislukk */
        if(solidsemop(xm->semid, ops, 1) == 0){
            retval= 0;
            xm->wl_count++;
        }else{
            xm->error= errno;
        }
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

int xmem_unlock(struct xmem* xm)
{
    int retval= -1;
    struct sembuf ops[1];

    xm->error= 0;

    /* kui on rohkem kui yks lugemislukk, vahendame lihstalt oma loendurit */
    if(xm->rl_count > 1){
        xm->rl_count--;
        retval= 0;
    /* kui on rohkem kui yks kirjutamislukk, vahendame lihstalt oma loendurit */
    }else if(xm->wl_count > 1){
        xm->wl_count--;
        retval= 0;
    /* kui meil ikka on semafoor kenasti kyljes */
    }else if(xm->semid != -1){
        /* kui on viimane lugemislukk, anname luku vabaks */
        if(xm->rl_count == 1){
            ops[0].sem_num= 0;
            ops[0].sem_op= 1;
            ops[0].sem_flg= SEM_UNDO;

            /* vabastamine ei blokeeru iial */
            if(solidsemop(xm->semid, ops, 1) == 0){
                retval= 0;
                xm->rl_count--;
            }else{
                xm->error= errno;
            }
        /* kui on viimane kirjutamislukk, anname luku vabaks */
        }else if(xm->wl_count == 1){
            ops[0].sem_num= 0;
            ops[0].sem_op= MAXLOCKS;
            ops[0].sem_flg= SEM_UNDO;

            /* vabastamine ei blokeeru iial */
            if(solidsemop(xm->semid, ops, 1) == 0){
                retval= 0;
                xm->wl_count--;
            }else{
                xm->error= errno;
            }
        }else{
            xm->error= EINVAL;
        }
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

size_t xmem_len(struct xmem* xm)
{
    size_t retval= 0;

    xm->error= 0;

    /* segment peab olema lukus ja viidasegment attachitud */
    if((xm->rl_count || xm->wl_count) && xm->pshmptr){
        retval= *((size_t *)((char *)xm->pshmptr+ sizeof(int)));
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

void* xmem_ptr(struct xmem* xm)
{
    void* retval= 0;

    xm->error= 0;

    /* segment peab olema lukus ja viidasegment attachitud */
    if((xm->rl_count || xm->wl_count) && xm->pshmptr){
        int current_dshmid= *((int *)xm->pshmptr);

        if(current_dshmid == xm->dshmid){
            /* sama segment, seega pole vaja ymber attachida */
            retval= xm->dshmptr;
        }else{
            /* uus segment. anname vana vabaks ja votame uue kylge */
            if(xm->dshmptr != 0){
                shmdt(xm->dshmptr);
            }
            xm->dshmptr= 0;
            xm->dshmid= -1;

            /* kui id on -1, siis segment puudub ja anname tagasi 0 */
            if(current_dshmid != -1){
                /* ja haagime selle kylge */
                if((xm->dshmptr= shmat(current_dshmid, 0, 0)) != (void *)-1){
                    xm->dshmid= current_dshmid;
                    retval= xm->dshmptr;
                }else{
                    xm->error= errno;
                    xm->dshmptr= 0;
                }
            }
        }
    }else{
        xm->error= EINVAL;
    }
    return(retval);
}

