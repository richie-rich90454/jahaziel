/*
 * Copyright 2026 The Jahaziel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package dev.jahaziel.spi;

import java.nio.file.Path;

/** Resolves and fetches immutable base images. */
@JahazielSpi(version = 1)
public abstract class ImageRegistry {
    /**
     * Resolves a reference to a concrete image.
     *
     * @param reference the image reference, for example {@code base-image:2026.09}
     * @return the resolved image
     */
    public abstract ImageRef resolve(String reference);

    /**
     * Fetches an image to a destination path.
     *
     * @param ref the resolved image
     * @param destination the destination path
     */
    public abstract void pull(ImageRef ref, Path destination);
}
