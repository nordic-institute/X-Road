XROADTOP = $(shell cd ../ ; pwd)

JAVA_HOME ?= $(shell dirname $$(dirname $$(readlink -f $$(which javac))))

LIBSRC = xmem.c passwordstore.ci ee_ria_xroad_common_util_MemoryPasswordStoreProvider.c

OBJECTS = xmem.o passwordstore.o ee_ria_xroad_common_util_MemoryPasswordStoreProvider.o

TEST_PROGRAMS = test_passwordstore_write test_passwordstore_read \
		test_passwordstore_clear

AUTOMATIC_TESTS =

LIBRARY = libpasswordstore.so

PROGRAMS =

LDFLAGS = -L. -lpasswordstore

USE_PIC=1

CFLAGS += -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux

all: $(LIBRARY) $(PROGRAMS)

$(LIBRARY): $(OBJECTS)
	$(CC) -o $@ $+ -shared -fPIC

clean:
	$(RM) *.o *.so core $(TEST_PROGRAMS)

test_passwordstore_read: test_passwordstore_read.o $(LIBRARY)
	$(CC) -o $@ $+ $(LDFLAGS)

test_passwordstore_write: test_passwordstore_write.o $(LIBRARY)
	$(CC) -o $@ $+ $(LDFLAGS)

test_passwordstore_clear: test_passwordstore_clear.o $(LIBRARY)
	$(CC) -o $@ $+ $(LDFLAGS)

include ../Makefile.common
