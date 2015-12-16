XROADTOP = $(shell pwd)

LIBSRC = ee_cyber_xroad_confimporter_XLock.c xlock.c

OBJECTS = ee_cyber_xroad_confimporter_XLock.o xlock.o

TEST_PROGRAMS = test_xlock

AUTOMATIC_TESTS = test_xlock_auto.sh

LIBRARY = libxlock.so

PROGRAMS =

LDFLAGS = -L. -lxlock

USE_PIC=1

CFLAGS += -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux

all: $(LIBRARY) $(PROGRAMS)

$(LIBRARY): $(OBJECTS)
	$(CC) -o $@ $+ -shared -fPIC

clean:
	$(RM) *.o *.so core $(TEST_PROGRAMS)

test_xlock: test_xlock.o $(LIBRARY)
	$(CC) -o $@ $+ $(LDFLAGS)

include ../../../../Makefile.common
