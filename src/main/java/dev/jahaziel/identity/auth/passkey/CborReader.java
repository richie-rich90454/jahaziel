/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

import java.nio.charset.StandardCharsets;

/**
 * Small definite-length CBOR reader covering only the types WebAuthn uses: integers, byte strings,
 * text strings, arrays, maps, tags, and simple values.
 */
final class CborReader {
    private final byte[] data;
    private int position;

    CborReader(byte[] data) {
        if (data == null) {
            throw new PasskeyException("CBOR input is missing");
        }
        this.data = data;
    }

    boolean hasMore() {
        return position < data.length;
    }

    int peekMajor() {
        require(1);
        return (data[position] & 0xFF) >>> 5;
    }

    long readInteger() {
        long[] head = readHead();
        if (head[0] == 0L) {
            return head[1];
        }
        if (head[0] == 1L) {
            return -1L - head[1];
        }
        throw new PasskeyException("CBOR value is not an integer");
    }

    byte[] readByteString() {
        long[] head = readHead();
        if (head[0] != 2L) {
            throw new PasskeyException("CBOR value is not a byte string");
        }
        return readPayload(head[1]);
    }

    String readTextString() {
        long[] head = readHead();
        if (head[0] != 3L) {
            throw new PasskeyException("CBOR value is not a text string");
        }
        return new String(readPayload(head[1]), StandardCharsets.UTF_8);
    }

    int readArrayLength() {
        long[] head = readHead();
        if (head[0] != 4L) {
            throw new PasskeyException("CBOR value is not an array");
        }
        return length(head[1]);
    }

    int readMapLength() {
        long[] head = readHead();
        if (head[0] != 5L) {
            throw new PasskeyException("CBOR value is not a map");
        }
        return length(head[1]);
    }

    void skipValue() {
        long[] head = readHead();
        long major = head[0];
        long argument = head[1];
        if (major == 0L || major == 1L) {
            return;
        }
        if (major == 2L || major == 3L) {
            skip(argument);
            return;
        }
        if (major == 4L) {
            int count = length(argument);
            for (int index = 0; index < count; index++) {
                skipValue();
            }
            return;
        }
        if (major == 5L) {
            int count = length(argument);
            for (int index = 0; index < count; index++) {
                skipValue();
                skipValue();
            }
            return;
        }
        if (major == 6L) {
            skipValue();
            return;
        }
        if (major == 7L) {
            long additional = head[2];
            if (additional == 24L) {
                skip(1L);
            } else if (additional == 25L) {
                skip(2L);
            } else if (additional == 26L) {
                skip(4L);
            } else if (additional == 27L) {
                skip(8L);
            }
            return;
        }
        throw new PasskeyException("unsupported CBOR major type");
    }

    private long[] readHead() {
        require(1);
        int first = data[position++] & 0xFF;
        int major = first >>> 5;
        int additional = first & 0x1F;
        long argument;
        if (additional < 24) {
            argument = additional;
        } else if (additional == 24) {
            require(1);
            argument = readUnsigned(1);
        } else if (additional == 25) {
            require(2);
            argument = readUnsigned(2);
        } else if (additional == 26) {
            require(4);
            argument = readUnsigned(4);
        } else if (additional == 27) {
            require(8);
            argument = readUnsigned(8);
        } else {
            throw new PasskeyException("unsupported CBOR length encoding");
        }
        return new long[] {major, argument, additional};
    }

    private long readUnsigned(int count) {
        long value = 0L;
        for (int index = 0; index < count; index++) {
            value = (value << 8) | (long) (data[position++] & 0xFF);
        }
        return value;
    }

    private byte[] readPayload(long size) {
        int count = length(size);
        require(count);
        byte[] value = new byte[count];
        System.arraycopy(data, position, value, 0, count);
        position += count;
        return value;
    }

    private void skip(long count) {
        int length = length(count);
        require(length);
        position += length;
    }

    private int length(long value) {
        if (value < 0L || value > Integer.MAX_VALUE) {
            throw new PasskeyException("CBOR length is out of range");
        }
        return (int) value;
    }

    private void require(int count) {
        if (count < 0 || position + count > data.length) {
            throw new PasskeyException("truncated CBOR input");
        }
    }
}
