package com.scrambler.app;

import com.scrambler.archive.ZipCreator;
import com.scrambler.archive.ZipExtractor;
import com.scrambler.classify.ClassificationResult;
import com.scrambler.classify.FileCategory;
import com.scrambler.classify.FileClassifier;
import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;
import com.scrambler.exception.FileProcessingException;
import com.scrambler.exception.MaskingException;
import com.scrambler.exception.ReportException;
import com.scrambler.file.TextFileReader;
import com.scrambler.file.TextFileWriter;
import com.scrambler.inventory.FileInfo;
import com.scrambler.inventory.FileIterator;
import com.scrambler.inventory.RepositoryInventory;
import com.scrambler.report.EntityReportRecord;
import com.scrambler.report.ReportDigest;
import com.scrambler.unmasking.MappingIndex;
import com.scrambler.unmasking.MappingLoader;
import com.scrambler.unmasking.RestoreResult;
import com.scrambler.unmasking.RestoreValidator;
import com.scrambler.unmasking.UnmaskingEngine;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Entry point for the unmasking CLI.
 * Milestone 7 restores TEXT content from a masked repository archive and entity report.
 */
public final class UnmaskingApplication {

    public static final String OUTPUT_ARCHIVE_NAME = "original_repo.zip";

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INVALID_USAGE = 1;
    static final int EXIT_PROCESSING_FAILURE = 2;

    private final WorkspaceManager workspaceManager;
    private final ZipExtractor zipExtractor;
    private final ZipCreator zipCreator;
    private final FileIterator fileIterator;
    private final FileClassifier fileClassifier;
    private final MappingLoader mappingLoader;
    private final RestoreValidator restoreValidator;
    private final UnmaskingEngine unmaskingEngine;
    private final TextFileReader textFileReader;
    private final TextFileWriter textFileWriter;
    private final ScramblerConfig config;

    /**
     * Creates the unmasking application with default collaborators.
     */
    public UnmaskingApplication() {
        this(ScramblerConfig.defaults());
    }

    /**
     * Creates the unmasking application with the provided configuration.
     *
     * @param config run configuration
     */
    public UnmaskingApplication(ScramblerConfig config) {
        this.config = config;
        this.workspaceManager = new WorkspaceManager();
        this.zipExtractor = new ZipExtractor(workspaceManager, config);
        this.zipCreator = new ZipCreator(workspaceManager);
        this.fileIterator = new FileIterator(workspaceManager);
        this.fileClassifier = new FileClassifier();
        this.mappingLoader = new MappingLoader();
        this.restoreValidator = new RestoreValidator();
        this.unmaskingEngine = new UnmaskingEngine();
        this.textFileReader = new TextFileReader();
        this.textFileWriter = new TextFileWriter();
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments; expects masked repository ZIP and entity report paths
     */
    public static void main(String[] args) {
        int exitCode = new UnmaskingApplication().run(args);
        System.exit(exitCode);
    }

    /**
     * Executes the restoration workflow and returns the process exit code.
     *
     * @param args command-line arguments
     * @return exit code per the architecture contract
     */
    public int run(String[] args) {
        if (args == null || args.length != 2) {
            printUsage();
            return EXIT_INVALID_USAGE;
        }

        Path maskedZipPath = Paths.get(args[0]);
        Path reportPath = Paths.get(args[1]);
        Path outputZipPath = resolveOutputPath(maskedZipPath);

        Workspace workspace = null;
        try {
            workspace = workspaceManager.createWorkspace(config);
            Path extractionRoot = zipExtractor.extract(maskedZipPath, workspace);
            List<EntityReportRecord> records = mappingLoader.load(reportPath);
            verifyReportDigest(reportPath);
            restoreValidator.validate(records);
            MappingIndex mappingIndex = MappingIndex.from(records);
            RestoreResult restoreResult = restoreTextFiles(
                    new RepositoryInventory(fileIterator.collectFiles(extractionRoot)),
                    mappingIndex);
            zipCreator.create(extractionRoot, outputZipPath, workspace);
            printSummary(restoreResult, outputZipPath);
            return EXIT_SUCCESS;
        } catch (ArchiveException | FileProcessingException | MaskingException | ReportException e) {
            return reportProcessingFailure(e);
        } catch (RuntimeException e) {
            return reportProcessingFailure(e);
        } finally {
            workspaceManager.cleanup(workspace);
        }
    }

    private RestoreResult restoreTextFiles(RepositoryInventory inventory, MappingIndex mappingIndex)
            throws FileProcessingException {
        RestoreResult restoreResult = new RestoreResult();

        for (FileInfo fileInfo : inventory.getFiles()) {
            restoreResult.incrementFilesProcessed();

            ClassificationResult classification = fileClassifier.classify(fileInfo);
            if (classification.getCategory() != FileCategory.TEXT) {
                continue;
            }

            String maskedContent = textFileReader.readUtf8(fileInfo.getAbsolutePath());
            int tokensBefore = restoreResult.getTokensRestored();
            String restoredContent = unmaskingEngine.unmask(maskedContent, mappingIndex, restoreResult);
            textFileWriter.writeUtf8(fileInfo.getAbsolutePath(), restoredContent);

            if (restoreResult.getTokensRestored() > tokensBefore) {
                restoreResult.incrementFilesRestored();
            }
        }

        return restoreResult;
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
        System.err.println("Usage: java -jar scramble-unmask.jar <masked_repo.zip> <entity_report.csv>");
    }

    private static void verifyReportDigest(Path reportPath) {
        Path digestPath = reportPath.resolveSibling(ReportDigest.DIGEST_FILENAME);
        if (Files.isRegularFile(digestPath)) {
            ReportDigest.verify(reportPath, digestPath);
        }
    }

    private static Path resolveOutputPath(Path maskedZipPath) {
        Path outputDirectory = maskedZipPath.toAbsolutePath().getParent();
        if (outputDirectory == null) {
            outputDirectory = Paths.get(".");
        }
        return outputDirectory.resolve(OUTPUT_ARCHIVE_NAME);
    }

    private static void printSummary(RestoreResult restoreResult, Path outputZipPath) {
        System.out.println("Files Processed: " + restoreResult.getFilesProcessed());
        System.out.println("Files Restored: " + restoreResult.getFilesRestored());
        System.out.println("Tokens Restored: " + restoreResult.getTokensRestored());
        System.out.println("Output Archive:");
        System.out.println(outputZipPath.toAbsolutePath().normalize());
    }
}
