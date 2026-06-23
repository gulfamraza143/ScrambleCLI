package com.scrambler.path;

import java.nio.file.Path;

/**
 * Result of path tokenization applied to an extracted repository tree.
 *
 * @param repositoryRoot   directory containing masked repository contents for packaging
 * @param repositoryFolder name of the repository folder inside the output ZIP (may be tokenized)
 * @param outputZipName    final output ZIP filename including {@code .zip} extension
 */
public record PathTokenizationResult(
        Path repositoryRoot,
        String repositoryFolder,
        String outputZipName) {
}
