/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.identity.auth.passkey;

/** Parsed COSE_Key for an ES256 (P-256) credential public key. */
public final class CoseKey {
    private static final long KEY_TYPE = 1L;
    private static final long ALGORITHM = 3L;
    private static final long CURVE = -1L;
    private static final long X = -2L;
    private static final long Y = -3L;
    private static final long EC2 = 2L;
    private static final long ES256 = -7L;
    private static final long P256 = 1L;

    private final byte[] x;
    private final byte[] y;

    private CoseKey(byte[] x, byte[] y) {
        this.x = x;
        this.y = y;
    }

    public byte[] x() {
        return x.clone();
    }

    public byte[] y() {
        return y.clone();
    }

    public static CoseKey parse(byte[] encoded) {
        CborReader reader = new CborReader(encoded);
        int count = reader.readMapLength();
        Long type = null;
        Long algorithm = null;
        Long curve = null;
        byte[] x = null;
        byte[] y = null;
        for (int index = 0; index < count; index++) {
            long key = reader.readInteger();
            int major = reader.peekMajor();
            if (major == 2) {
                byte[] value = reader.readByteString();
                if (key == X) {
                    x = value;
                } else if (key == Y) {
                    y = value;
                }
            } else if (major == 0 || major == 1) {
                long value = reader.readInteger();
                if (key == KEY_TYPE) {
                    type = Long.valueOf(value);
                } else if (key == ALGORITHM) {
                    algorithm = Long.valueOf(value);
                } else if (key == CURVE) {
                    curve = Long.valueOf(value);
                }
            } else {
                reader.skipValue();
            }
        }
        if (type == null || type.longValue() != EC2) {
            throw new PasskeyException("passkey public key is not EC2");
        }
        if (algorithm == null || algorithm.longValue() != ES256) {
            throw new PasskeyException("passkey algorithm is not ES256");
        }
        if (curve == null || curve.longValue() != P256) {
            throw new PasskeyException("passkey curve is not P-256");
        }
        if (x == null || y == null || x.length != 32 || y.length != 32) {
            throw new PasskeyException("passkey public key coordinates are invalid");
        }
        return new CoseKey(x, y);
    }
}
