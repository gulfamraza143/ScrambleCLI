package com.scrambler.app;

import com.scrambler.archive.ZipExtractor;
import com.scrambler.classify.ClassificationResult;
import com.scrambler.classify.FileCategory;
import com.scrambler.classify.FileClassifier;
import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.exception.ArchiveException;
import com.scrambler.exception.FileProcessingException;
import com.scrambler.file.TextFileReader;
import com.scrambler.masking.MappingRecord;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.masking.MaskingEngine;
import com.scrambler.inventory.FileInfo;
import com.scrambler.inventory.FileIterator;
import com.scrambler.inventory.RepositoryInventory;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Entry point for the masking CLI.
 * Milestone 4 extends detection with reversible token masking for text files.
 */
public final class MaskingApplication {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INVALID_USAGE = 1;
    static final int EXIT_PROCESSING_FAILURE = 2;

    private static final int ENTITY_TYPE_DISPLAY_WIDTH = 13;

    private final WorkspaceManager workspaceManager;
    private final ZipExtractor zipExtractor;
    private final FileIterator fileIterator;
    private final FileClassifier fileClassifier;
    private final DetectionEngine detectionEngine;
    private final MaskingEngine maskingEngine;
    private final TextFileReader textFileReader;
    private final ScramblerConfig config;

    /**
     * Creates the masking application with default collaborators.
     */
    public MaskingApplication() {
        this(ScramblerConfig.defaults());
    }

    /**
     * Creates the masking application with the provided configuration.
     *
     * @param config run configuration
     */
    public MaskingApplication(ScramblerConfig config) {
        this.config = config;
        this.workspaceManager = new WorkspaceManager();
        this.zipExtractor = new ZipExtractor(workspaceManager, config);
        this.fileIterator = new FileIterator(workspaceManager);
        this.fileClassifier = new FileClassifier();
        this.detectionEngine = new DetectionEngine();
        this.maskingEngine = new MaskingEngine();
        this.textFileReader = new TextFileReader();
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments; expects a single repository ZIP path
     */
    public static void main(String[] args) {
        int exitCode = new MaskingApplication().run(args);
        System.exit(exitCode);
    }

    /**
     * Executes milestone processing and returns the process exit code.
     *
     * @param args command-line arguments
     * @return exit code per the architecture contract
     */
    public int run(String[] args) {
        if (args == null || args.length != 1) {
            printUsage();
            return EXIT_INVALID_USAGE;
        }

        Path zipPath = Paths.get(args[0]);

        Workspace workspace = null;
        try {
            workspace = workspaceManager.createWorkspace(config);
            Path extractionRoot = zipExtractor.extract(zipPath, workspace);
            RepositoryInventory inventory = new RepositoryInventory(fileIterator.collectFiles(extractionRoot));
            MappingRegistry mappingRegistry = new MappingRegistry();
            List<MaskedFileResult> maskedFiles = maskTextFiles(inventory, mappingRegistry);
            printMaskedResults(maskedFiles, mappingRegistry);
            return EXIT_SUCCESS;
        } catch (ArchiveException | FileProcessingException e) {
            return reportProcessingFailure(e);
        } catch (RuntimeException e) {
            return reportProcessingFailure(e);
        } finally {
            workspaceManager.cleanup(workspace);
        }
    }

    private List<MaskedFileResult> maskTextFiles(
            RepositoryInventory inventory,
            MappingRegistry mappingRegistry) throws FileProcessingException {
        List<MaskedFileResult> maskedFiles = new ArrayList<>();

        for (FileInfo fileInfo : inventory.getFiles()) {
            ClassificationResult classification = fileClassifier.classify(fileInfo);
            if (classification.getCategory() != FileCategory.TEXT) {
                continue;
            }

            String content = textFileReader.readUtf8(fileInfo.getAbsolutePath());
            DetectionResult detectionResult = detectionEngine.detect(new DetectionContext(fileInfo, content));
            String maskedContent = maskingEngine.mask(content, detectionResult, mappingRegistry);
            if (detectionResult.hasEntities()) {
                maskedFiles.add(new MaskedFileResult(fileInfo.getRepoRelativePath(), maskedContent));
            }
        }

        maskedFiles.sort(Comparator.comparing(MaskedFileResult::repoRelativePath));
        return maskedFiles;
    }

    private static int reportProcessingFailure(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        System.err.println("Processing failed: " + message);
        return EXIT_PROCESSING_FAILURE;
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar scramble-mask.jar <repo.zip>");
    }

    private static void printMaskedResults(List<MaskedFileResult> maskedFiles, MappingRegistry mappingRegistry) {
        System.out.println("===== MASKED FILES =====");
        System.out.println();

        for (MaskedFileResult maskedFile : maskedFiles) {
            System.out.println("File:");
            System.out.println(maskedFile.repoRelativePath());
            System.out.println();
            System.out.println(maskedFile.maskedContent());
            System.out.println();
        }

        if (maskedFiles.isEmpty()) {
            System.out.println("No sensitive entities detected.");
            System.out.println();
        }

        System.out.println("===== MAPPINGS =====");
        System.out.println();

        for (MappingRecord record : mappingRegistry.getRecords()) {
            String typeLabel = String.format("%-" + ENTITY_TYPE_DISPLAY_WIDTH + "s", record.getEntityType().name());
            System.out.println(record.getRepoRelativePath() + " | " + typeLabel + record.getMaskedValue());
        }

        if (mappingRegistry.getRecords().isEmpty()) {
            System.out.println("No mappings recorded.");
            System.out.println();
        }
    }

    private record MaskedFileResult(String repoRelativePath, String maskedContent) {
    }
}
