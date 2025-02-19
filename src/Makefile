XROADTOP = $(shell pwd)

ARCH := $(shell uname -m)

ifeq ($(ARCH),x86_64)
    ARCH_DIR := amd64
else ifeq ($(ARCH),aarch64)
    ARCH_DIR := arm64
else
    $(error Unsupported architecture: $(ARCH))
endif

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
    rm -f libs/$$i/$(ARCH_DIR)/* || exit 1 ;\
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
