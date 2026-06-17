package com.scrambler.app;

import com.scrambler.archive.ZipExtractor;
import com.scrambler.classify.ClassificationResult;
import com.scrambler.classify.FileClassifier;
import com.scrambler.config.ScramblerConfig;
import com.scrambler.exception.ArchiveException;
import com.scrambler.exception.FileProcessingException;
import com.scrambler.inventory.FileInfo;
import com.scrambler.inventory.FileIterator;
import com.scrambler.inventory.RepositoryInventory;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point for the masking CLI.
 * Milestone 1 extracts a repository archive and prints an inventory summary.
 */
public final class MaskingApplication {

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INVALID_USAGE = 1;
    static final int EXIT_PROCESSING_FAILURE = 2;

    private static final int CATEGORY_DISPLAY_WIDTH = 10;

    private final WorkspaceManager workspaceManager;
    private final ZipExtractor zipExtractor;
    private final FileIterator fileIterator;
    private final FileClassifier fileClassifier;
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
     * Executes milestone 1 processing and returns the process exit code.
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
            printInventory(inventory, fileClassifier);
            return EXIT_SUCCESS;
        } catch (ArchiveException | FileProcessingException e) {
            return reportProcessingFailure(e);
        } catch (RuntimeException e) {
            return reportProcessingFailure(e);
        } finally {
            workspaceManager.cleanup(workspace);
        }
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

    private static void printInventory(RepositoryInventory inventory, FileClassifier fileClassifier) {
        System.out.println("===== INVENTORY =====");
        System.out.println();

        for (FileInfo fileInfo : inventory.getFiles()) {
            ClassificationResult result = fileClassifier.classify(fileInfo);
            String categoryLabel = String.format("%-" + CATEGORY_DISPLAY_WIDTH + "s", result.getCategory().name());
            System.out.println(categoryLabel + result.getFileInfo().getRepoRelativePath());
        }

        System.out.println();
        System.out.println("Total Files: " + inventory.getTotalFileCount());
    }
}
