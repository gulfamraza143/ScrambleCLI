package com.scrambler.app;

import com.scrambler.archive.ArchiveExtractor;
import com.scrambler.archive.MaskedOutputPackager;
import com.scrambler.archive.NestedArchiveProcessor;
import com.scrambler.classify.ClassificationResult;
import com.scrambler.classify.FileCategory;
import com.scrambler.classify.FileClassifier;
import com.scrambler.config.ScramblerConfig;
import com.scrambler.detection.DetectionContext;
import com.scrambler.detection.DetectionEngine;
import com.scrambler.detection.DetectionResult;
import com.scrambler.exception.AlreadyMaskedException;
import com.scrambler.exception.ArchiveException;
import com.scrambler.exception.FileProcessingException;
import com.scrambler.exception.ReportException;
import com.scrambler.repository.RepositoryMetadata;
import com.scrambler.file.TextFileReader;
import com.scrambler.file.TextFileWriter;
import com.scrambler.masking.MappingRegistry;
import com.scrambler.masking.MaskingEngine;
import com.scrambler.path.PathTokenizationResult;
import com.scrambler.path.PathTokenizer;
import com.scrambler.report.ReportSchema;
import com.scrambler.report.XlsxReportWriter;
import com.scrambler.inventory.FileInfo;
import com.scrambler.inventory.FileIterator;
import com.scrambler.inventory.RepositoryInventory;
import com.scrambler.replacement.BinaryPlaceholderCopier;
import com.scrambler.replacement.ReplacementPlan;
import com.scrambler.workspace.Workspace;
import com.scrambler.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Orchestrates the SCRAMBLE Enterprise format-preserving masking pipeline.
 *
 * <p>Input: repository archive ({@code .zip}, {@code .tar}, {@code .tgz}, {@code .7z}) or folder.
 *
 * <p>Processing stages:
 * Workspace Creation → Archive Extraction → Nested Archive Expansion → Path Tokenization → Inventory →
 * Classification → Detection → Format-Preserving Masking → Placeholder Replacement → Report Generation → Re-Zipping
 *
 * <p>Output: a token-named ZIP containing the masked repository folder and a separate
 * {@link ReportSchema#REPORT_FILENAME} written beside the input archive.
 */
public final class MaskingApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MaskingApplication.class);

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INVALID_USAGE = 1;
    static final int EXIT_PROCESSING_FAILURE = 2;

    private static final int PIPELINE_STAGES = 10;
    private static final int FILE_PROCESSING_PROGRESS_WIDTH = 40;
    private static final char FILE_PROCESSING_PROGRESS_FILLED = '\u2588';
    private static final char FILE_PROCESSING_PROGRESS_EMPTY = '-';

    private final WorkspaceManager workspaceManager;
    private final ArchiveExtractor archiveExtractor;
    private final NestedArchiveProcessor nestedArchiveProcessor;
    private final MaskedOutputPackager maskedOutputPackager;
    private final FileIterator fileIterator;
    private final FileClassifier fileClassifier;
    private final DetectionEngine detectionEngine;
    private final MaskingEngine maskingEngine;
    private final PathTokenizer pathTokenizer;
    private final BinaryPlaceholderCopier binaryPlaceholderCopier;
    private final XlsxReportWriter xlsxReportWriter;
    private final TextFileReader textFileReader;
    private final TextFileWriter textFileWriter;
    private final ScramblerConfig config;

    private int pipelineTextCount;
    private int pipelineImageCount;
    private int pipelineDocumentCount;
    private int pipelineSkipCount;
    private int pipelineFilesScanned;
    private int pipelineFilesWithEntities;
    private int pipelineEntitiesFound;
    private int pipelineArchivesExpanded;

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
        this.archiveExtractor = new ArchiveExtractor(workspaceManager, config);
        this.fileIterator = new FileIterator(workspaceManager);
        this.nestedArchiveProcessor = new NestedArchiveProcessor(fileIterator, archiveExtractor);
        this.maskedOutputPackager = new MaskedOutputPackager(workspaceManager);
        this.fileClassifier = new FileClassifier();
        this.detectionEngine = new DetectionEngine();
        this.maskingEngine = new MaskingEngine();
        this.pathTokenizer = new PathTokenizer();
        this.binaryPlaceholderCopier = new BinaryPlaceholderCopier();
        this.xlsxReportWriter = new XlsxReportWriter();
        this.textFileReader = new TextFileReader();
        this.textFileWriter = new TextFileWriter();
    }

    /**
     * Application entry point.
     *
     * @param args command-line arguments; expects a single repository archive or folder path
     */
    public static void main(String[] args) {
        int exitCode = new MaskingApplication().run(args);
        System.exit(exitCode);
    }

    /**
     * Executes the masking pipeline and returns the process exit code.
     *
     * @param args command-line arguments
     * @return exit code per the architecture contract
     */
    public int run(String[] args) {
        if (args == null || args.length != 1) {
            printUsage();
            return EXIT_INVALID_USAGE;
        }

        Path inputPath = Paths.get(args[0]);
        LOGGER.info("Starting masking pipeline for input: {}", inputPath.toAbsolutePath().normalize());

        Workspace workspace = null;
        try {
            if (Files.isDirectory(inputPath)) {
                RepositoryMetadata.ensureNotAlreadyMasked(inputPath);
            }

            printStage(1, "Workspace Creation");
            LOGGER.info("Stage 1 - Workspace Creation");
            workspace = workspaceManager.createWorkspace(config);
            printSuccess("Workspace Ready");
            printDetail("Workspace Path", workspace.getRootPath());
            LOGGER.info("Workspace ready at {}", workspace.getRootPath());
            System.out.println();

            printStage(2, "Repository Extraction");
            LOGGER.info("Stage 2 - Repository Extraction");
            Path extractionRoot = archiveExtractor.extract(inputPath, workspace);
            String repositoryName = PathTokenizer.deriveRepositoryName(inputPath);
            Path repositoryContentRoot = PathTokenizer.resolveContentRoot(extractionRoot, repositoryName);
            RepositoryMetadata.ensureNotAlreadyMasked(repositoryContentRoot);
            printSuccess("Repository Extracted");
            printDetail("Extraction Root", extractionRoot);
            LOGGER.info("Repository extracted to {}", extractionRoot);
            System.out.println();

            printStage(3, "Nested Archive Expansion");
            LOGGER.info("Stage 3 - Nested Archive Expansion");
            pipelineArchivesExpanded = nestedArchiveProcessor.expandArchives(extractionRoot, workspace);
            printMetric("Archives Expanded", pipelineArchivesExpanded);
            LOGGER.info("Nested archive expansion complete; archives expanded: {}", pipelineArchivesExpanded);
            System.out.println();

            MappingRegistry mappingRegistry = new MappingRegistry();
            resetPipelineStats();

            printStage(4, "Path Tokenization");
            LOGGER.info("Stage 4 - Path Tokenization");
            PathTokenizationResult pathTokenization = pathTokenizer.tokenize(
                    extractionRoot,
                    repositoryName,
                    maskingEngine.getGlobalValueMapper(),
                    mappingRegistry);
            printSuccess("Repository Folder: " + pathTokenization.repositoryFolder());
            LOGGER.info("Repository folder token: {}", pathTokenization.repositoryFolder());
            LOGGER.info("Path tokenization complete; output archive name: {}", pathTokenization.outputZipName());
            System.out.println();

            printStage(5, "Repository Inventory");
            LOGGER.info("Stage 5 - Repository Inventory");
            RepositoryInventory inventory = new RepositoryInventory(
                    fileIterator.collectFiles(pathTokenization.repositoryRoot()));
            int filesDiscovered = inventory.getFiles().size();
            printSuccess("Files Discovered: " + filesDiscovered);
            LOGGER.info("Inventory size: {}", filesDiscovered);
            System.out.println();

            printStage(6, "File Classification");
            LOGGER.info("Stage 6 - File Classification, Detection, and Masking");
            long stage6StartNanos = System.nanoTime();
            int maskedFilesCount = maskTextFiles(inventory, mappingRegistry);
            printStage6CompletionTime(System.nanoTime() - stage6StartNanos);
            printClassificationSummary();
            System.out.println();

            printStage(7, "Sensitive Data Detection");
            LOGGER.info("Stage 7 - Sensitive Data Detection");
            printDetectionSummary();
            LOGGER.info("Detection complete; files scanned: {}, entities found: {}",
                    pipelineFilesScanned, pipelineEntitiesFound);
            System.out.println();

            printStage(8, "Format-Preserving Masking");
            LOGGER.info("Stage 8 - Format-Preserving Masking");
            printMetric("Unique Values Mapped", maskingEngine.getGlobalValueMapper().size());
            printMetric("Total Mappings Registered", mappingRegistry.getRecords().size());
            printMetric("Files Masked", maskedFilesCount);
            System.out.println();

            printStage(9, "Binary Replacement");
            LOGGER.info("Stage 9 - Binary Replacement");
            int placeholdersReplaced = replaceBinaryAssets(inventory);
            printMetric("Placeholders Applied", placeholdersReplaced);
            System.out.println();

            printStage(10, "Output Generation");
            LOGGER.info("Stage 10 - Output Generation");
            Path outputDirectory = resolveOutputDirectory(inputPath);
            Path reportPath = outputDirectory.resolve(ReportSchema.REPORT_FILENAME);
            xlsxReportWriter.write(mappingRegistry, reportPath);
            printSuccess(ReportSchema.REPORT_FILENAME);
            RepositoryMetadata.writeMarker(pathTokenization.repositoryRoot());
            Path maskedZipPath = outputDirectory.resolve(pathTokenization.outputZipName());
            maskedOutputPackager.create(
                    pathTokenization.repositoryRoot(),
                    pathTokenization.repositoryFolder(),
                    maskedZipPath,
                    workspace);
            printSuccess(pathTokenization.outputZipName());
            printDetail(ReportSchema.REPORT_FILENAME, reportPath.toAbsolutePath().normalize());
            printDetail("Output Archive", maskedZipPath);
            LOGGER.info("Output generation complete; report: {}, archive: {}", reportPath, maskedZipPath);
            System.out.println();

            printSummary(filesDiscovered, maskedFilesCount, mappingRegistry, placeholdersReplaced);
            LOGGER.info("Masking pipeline completed successfully");
            return EXIT_SUCCESS;
        } catch (AlreadyMaskedException e) {
            LOGGER.warn("Repository already masked: {}", e.getMessage());
            return reportAlreadyMasked();
        } catch (ArchiveException | FileProcessingException | ReportException e) {
            return reportProcessingFailure(e);
        } catch (IOException e) {
            return reportProcessingFailure(new FileProcessingException("Failed to write repository metadata marker", e));
        } catch (RuntimeException e) {
            return reportProcessingFailure(e);
        } finally {
            workspaceManager.cleanup(workspace);
        }
    }

    private int maskTextFiles(
            RepositoryInventory inventory,
            MappingRegistry mappingRegistry) throws FileProcessingException {
        int maskedFilesCount = 0;
        int totalFiles = inventory.getFiles().size();
        int processedFiles = 0;
        int lastPrintedPercent = -1;
        StringBuilder progressLine = totalFiles > 0 ? new StringBuilder(96) : null;
        char[] progressBar = totalFiles > 0 ? new char[FILE_PROCESSING_PROGRESS_WIDTH] : null;

        for (FileInfo fileInfo : inventory.getFiles()) {
            ClassificationResult classification = fileClassifier.classify(fileInfo);
            recordClassification(classification.getCategory());
            if (classification.getCategory() == FileCategory.TEXT) {
                LOGGER.debug("Processing file {}", fileInfo.getRepoRelativePath());
                Optional<String> contentOptional = textFileReader.readUtf8(fileInfo.getAbsolutePath());
                if (contentOptional.isPresent()) {
                    String content = contentOptional.get();
                    DetectionResult detectionResult = detectionEngine.detect(new DetectionContext(fileInfo, content));
                    recordDetection(detectionResult);
                    String maskedContent = maskingEngine.mask(content, detectionResult, mappingRegistry);
                    if (detectionResult.hasEntities()) {
                        textFileWriter.writeUtf8(fileInfo.getAbsolutePath(), maskedContent);
                        maskedFilesCount++;
                    }
                }
            }

            processedFiles++;
            if (totalFiles > 0) {
                lastPrintedPercent = printFileProcessingProgressIfChanged(
                        processedFiles, totalFiles, progressLine, progressBar, lastPrintedPercent);
            }
        }

        if (totalFiles > 0) {
            if (lastPrintedPercent < 100) {
                printFileProcessingProgress(totalFiles, totalFiles, 100, progressLine, progressBar);
            }
            System.out.println();
        }

        return maskedFilesCount;
    }

    private static int printFileProcessingProgressIfChanged(
            int processed,
            int total,
            StringBuilder line,
            char[] bar,
            int lastPrintedPercent) {
        int percent = (int) ((long) processed * 100 / total);
        if (percent == lastPrintedPercent) {
            return lastPrintedPercent;
        }
        printFileProcessingProgress(processed, total, percent, line, bar);
        return percent;
    }

    private static void printFileProcessingProgress(
            int processed,
            int total,
            int percent,
            StringBuilder line,
            char[] bar) {
        int filled = percent * FILE_PROCESSING_PROGRESS_WIDTH / 100;

        for (int i = 0; i < FILE_PROCESSING_PROGRESS_WIDTH; i++) {
            bar[i] = i < filled ? FILE_PROCESSING_PROGRESS_FILLED : FILE_PROCESSING_PROGRESS_EMPTY;
        }

        line.setLength(0);
        line.append("Processing Repository: [");
        line.append(bar);
        line.append("] ");
        line.append(percent);
        line.append("% (");
        line.append(processed);
        line.append('/');
        line.append(total);
        line.append(')');

        System.out.print('\r');
        System.out.print(line);
    }

    private static void printStage6CompletionTime(long elapsedNanos) {
        if (elapsedNanos < 1_000_000_000L) {
            System.out.printf("Completed in %d ms%n%n", elapsedNanos / 1_000_000L);
            return;
        }
        if (elapsedNanos < 60L * 1_000_000_000L) {
            long wholeSeconds = elapsedNanos / 1_000_000_000L;
            long tenths = (elapsedNanos % 1_000_000_000L) / 100_000_000L;
            if (tenths == 0) {
                if (wholeSeconds == 1) {
                    System.out.println("Completed in 1 second");
                } else {
                    System.out.printf("Completed in %d seconds%n", wholeSeconds);
                }
            } else {
                System.out.printf("Completed in %d.%d seconds%n", wholeSeconds, tenths);
            }
            System.out.println();
            return;
        }
        long totalSeconds = elapsedNanos / 1_000_000_000L;
        System.out.printf(
                "Completed in %d min %d sec%n%n",
                totalSeconds / 60,
                totalSeconds % 60);
    }

    private int replaceBinaryAssets(RepositoryInventory inventory) {
        ReplacementPlan replacementPlan = ReplacementPlan.from(inventory, fileClassifier);
        for (FileInfo fileInfo : replacementPlan.getReplacementTargets()) {
            binaryPlaceholderCopier.replace(fileInfo);
        }
        return replacementPlan.getReplacementTargets().size();
    }

    private void resetPipelineStats() {
        pipelineTextCount = 0;
        pipelineImageCount = 0;
        pipelineDocumentCount = 0;
        pipelineSkipCount = 0;
        pipelineFilesScanned = 0;
        pipelineFilesWithEntities = 0;
        pipelineEntitiesFound = 0;
    }

    private void recordClassification(FileCategory category) {
        switch (category) {
            case TEXT -> pipelineTextCount++;
            case IMAGE -> pipelineImageCount++;
            case DOCUMENT -> pipelineDocumentCount++;
            case SKIP -> pipelineSkipCount++;
        }
    }

    private void recordDetection(DetectionResult detectionResult) {
        pipelineFilesScanned++;
        int entityCount = detectionResult.getEntities().size();
        pipelineEntitiesFound += entityCount;
        if (entityCount > 0) {
            pipelineFilesWithEntities++;
        }
    }

    private void printClassificationSummary() {
        printCategoryCount("TEXT", pipelineTextCount);
        printCategoryCount("IMAGE", pipelineImageCount);
        printCategoryCount("DOCUMENT", pipelineDocumentCount);
        printCategoryCount("SKIP", pipelineSkipCount);
    }

    private void printDetectionSummary() {
        printMetric("Files Scanned", pipelineFilesScanned);
        printMetric("Files Containing Entities", pipelineFilesWithEntities);
        printMetric("Total Entities Detected", pipelineEntitiesFound);
    }

    private static void printStage(int step, String title) {
        System.out.println("[STEP " + step + "/" + PIPELINE_STAGES + "] " + title);
    }

    private static void printSuccess(String message) {
        System.out.println("    ✓ " + message);
    }

    private static void printMetric(String label, int value) {
        System.out.printf("    %-25s: %d%n", label, value);
    }

    private static void printDetail(String label, Path value) {
        System.out.printf("    %-25s: %s%n", label, value.toAbsolutePath().normalize());
    }

    private static void printCategoryCount(String category, int count) {
        System.out.printf("    %-10s: %d%n", category, count);
    }

    private void printSummary(
            int filesDiscovered,
            int maskedFilesCount,
            MappingRegistry mappingRegistry,
            int placeholdersReplaced) {
        System.out.println("==================================================");
        System.out.println("SUMMARY");
        System.out.println("==================================================");
        printMetric("Files Processed", filesDiscovered);
        printMetric("Archives Expanded", pipelineArchivesExpanded);
        printMetric("Files Masked", maskedFilesCount);
        printMetric("Entities Masked", mappingRegistry.getRecords().size());
        printMetric("Unique Values Mapped", maskingEngine.getGlobalValueMapper().size());
        printMetric("Placeholders Applied", placeholdersReplaced);
        System.out.println("==================================================");
    }

    private static int reportAlreadyMasked() {
        System.err.println("ERROR " + RepositoryMetadata.ERROR_HEADLINE);
        System.err.println(RepositoryMetadata.ERROR_DETAIL);
        return EXIT_PROCESSING_FAILURE;
    }

    private static int reportProcessingFailure(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        LOGGER.error("Processing failed", failure);
        System.err.println("Processing failed: " + message);
        return EXIT_PROCESSING_FAILURE;
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar scramble-mask.jar <repo.zip|folder>");
    }

    private static Path resolveOutputDirectory(Path inputPath) {
        Path outputDirectory = inputPath.toAbsolutePath().getParent();
        if (outputDirectory == null) {
            outputDirectory = Paths.get(".");
        }
        return outputDirectory;
    }
}
