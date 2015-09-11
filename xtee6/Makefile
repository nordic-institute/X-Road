XROADTOP = $(shell pwd)

SUBDIRS = passwordstore

all:
	for i in $(SUBDIRS) ;\
	do \
		( echo $$i && cd $$i && $(MAKE) && $(MAKE) install ) || exit 1 ;\
	done

clean:
	for i in $(SUBDIRS) ;\
	do \
		( echo $$i && cd $$i && $(MAKE) clean ) || exit 1 ;\
		rm -f lib/* || exit 1 ;\
	done

tests:
	for i in $(SUBDIRS) ;\
	do \
		( echo $$i && cd $$i && $(MAKE) tests ) || exit 1 ;\
	done

check:
	for i in $(SUBDIRS) ;\
	do \
		( echo $$i && cd $$i && $(MAKE) check && echo ) || exit 1 ;\
	done

.PHONY: legacy
