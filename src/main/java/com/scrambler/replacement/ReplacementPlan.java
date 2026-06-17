package com.scrambler.replacement;

import com.scrambler.classify.ClassificationResult;
import com.scrambler.classify.FileCategory;
import com.scrambler.classify.FileClassifier;
import com.scrambler.inventory.FileInfo;
import com.scrambler.inventory.RepositoryInventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Identifies inventoried files that require binary placeholder replacement.
 */
public final class ReplacementPlan {

    private final List<FileInfo> replacementTargets;

    private ReplacementPlan(List<FileInfo> replacementTargets) {
        this.replacementTargets = replacementTargets;
    }

    /**
     * Builds a replacement plan from a repository inventory and classifier output.
     *
     * @param inventory     discovered repository files
     * @param fileClassifier classifier used to determine DOCUMENT and IMAGE targets
     * @return immutable replacement plan
     */
    public static ReplacementPlan from(RepositoryInventory inventory, FileClassifier fileClassifier) {
        Objects.requireNonNull(inventory, "inventory must not be null");
        Objects.requireNonNull(fileClassifier, "fileClassifier must not be null");

        List<FileInfo> targets = new ArrayList<>();
        for (FileInfo fileInfo : inventory.getFiles()) {
            ClassificationResult classification = fileClassifier.classify(fileInfo);
            FileCategory category = classification.getCategory();
            if (category == FileCategory.DOCUMENT || category == FileCategory.IMAGE) {
                targets.add(fileInfo);
            }
        }
        return new ReplacementPlan(Collections.unmodifiableList(targets));
    }

    /**
     * Returns inventoried files scheduled for placeholder replacement.
     *
     * @return immutable list of replacement targets
     */
    public List<FileInfo> getReplacementTargets() {
        return replacementTargets;
    }
}
