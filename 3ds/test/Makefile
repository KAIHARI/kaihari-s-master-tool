# The conformance suite, built by the host compiler.
#
# Deliberately not devkitARM: src/core/ links no libctru precisely so that this
# can run anywhere, in a second, on every push. `make -C 3ds/test` is the local
# equivalent of `./gradlew :core:jvmTest`.

CC      ?= gcc
# -ffp-contract=off matters here and only here. GCC will otherwise fuse a*b+c
# into a single FMA, which is *more* accurate and therefore disagrees with the
# JVM, which does not. Over one spring step the difference is a ULP; over the
# forty steps of a trajectory it accumulates into a real one. A conformance
# suite wants the same arithmetic, not the best arithmetic.
CFLAGS  ?= -std=c99 -O1 -g -Wall -Wextra -Werror -Wno-unused-parameter \
           -ffp-contract=off
LDLIBS  := -lm

CORE    := $(wildcard ../src/core/*.c)
SRC     := mt_test.c $(CORE)
TARGET  := mt_test

.PHONY: all test clean
all: $(TARGET)

$(TARGET): $(SRC) $(wildcard ../src/core/*.h)
	$(CC) $(CFLAGS) -D_POSIX_C_SOURCE=200809L -o $@ $(SRC) $(LDLIBS)

test: $(TARGET)
	./$(TARGET)

clean:
	rm -f $(TARGET)
