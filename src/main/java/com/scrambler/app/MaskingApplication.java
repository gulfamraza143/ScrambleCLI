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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    static final int EXIT_SUCCESS = 0;
    static final int EXIT_INVALID_USAGE = 1;
    static final int EXIT_PROCESSING_FAILURE = 2;

    private static final int PIPELINE_STAGES = 10;

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

    private int pipelineFilesProcessed;
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

        Workspace workspace = null;
        try {
            if (Files.isDirectory(inputPath)) {
                RepositoryMetadata.ensureNotAlreadyMasked(inputPath);
            }

            printStage(1, "Workspace Creation");
            workspace = workspaceManager.createWorkspace(config);
            printSuccess("Workspace Ready");
            printDetail("Workspace Path", workspace.getRootPath());
            System.out.println();

            printStage(2, "Repository Extraction");
            Path extractionRoot = archiveExtractor.extract(inputPath, workspace);
            RepositoryMetadata.ensureNotAlreadyMasked(extractionRoot);
            printSuccess("Repository Extracted");
            printDetail("Extraction Root", extractionRoot);
            System.out.println();

            printStage(3, "Nested Archive Expansion");
            pipelineArchivesExpanded = nestedArchiveProcessor.expandArchives(extractionRoot, workspace);
            printMetric("Archives Expanded", pipelineArchivesExpanded);
            System.out.println();

            MappingRegistry mappingRegistry = new MappingRegistry();
            resetPipelineStats();

            printStage(4, "Path Tokenization");
            String repositoryName = PathTokenizer.deriveRepositoryName(inputPath);
            PathTokenizationResult pathTokenization = pathTokenizer.tokenize(
                    extractionRoot,
                    repositoryName,
                    maskingEngine.getGlobalValueMapper(),
                    mappingRegistry);
            printSuccess("Repository Folder: " + pathTokenization.repositoryFolder());
            System.out.println();

            printStage(5, "Repository Inventory");
            RepositoryInventory inventory = new RepositoryInventory(
                    fileIterator.collectFiles(pathTokenization.repositoryRoot()));
            pipelineFilesProcessed = inventory.getFiles().size();
            printSuccess("Files Discovered: " + pipelineFilesProcessed);
            printMetric("Total Files", pipelineFilesProcessed);
            System.out.println();

            printStage(6, "File Classification");
            List<MaskedFileResult> maskedFiles = maskTextFiles(inventory, mappingRegistry);
            printClassificationSummary();
            System.out.println();

            printStage(7, "Sensitive Data Detection");
            printDetectionSummary();
            System.out.println();

            printStage(8, "Format-Preserving Masking");
            printMetric("Unique Values Mapped", maskingEngine.getGlobalValueMapper().size());
            printMetric("Total Mappings Registered", mappingRegistry.getRecords().size());
            printMetric("Files Masked", maskedFiles.size());
            System.out.println();

            printStage(9, "Binary Replacement");
            int placeholdersReplaced = replaceBinaryAssets(inventory);
            printMetric("Placeholders Applied", placeholdersReplaced);
            System.out.println();

            printStage(10, "Output Generation");
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
            System.out.println();

            printSummary(maskedFiles, mappingRegistry, placeholdersReplaced);
            return EXIT_SUCCESS;
        } catch (AlreadyMaskedException e) {
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

    private List<MaskedFileResult> maskTextFiles(
            RepositoryInventory inventory,
            MappingRegistry mappingRegistry) throws FileProcessingException {
        List<MaskedFileResult> maskedFiles = new ArrayList<>();

        for (FileInfo fileInfo : inventory.getFiles()) {
            ClassificationResult classification = fileClassifier.classify(fileInfo);
            recordClassification(classification.getCategory());
            if (classification.getCategory() != FileCategory.TEXT) {
                continue;
            }

            String content = textFileReader.readUtf8(fileInfo.getAbsolutePath());
            DetectionResult detectionResult = detectionEngine.detect(new DetectionContext(fileInfo, content));
            recordDetection(detectionResult);
            String maskedContent = maskingEngine.mask(content, detectionResult, mappingRegistry);
            if (detectionResult.hasEntities()) {
                textFileWriter.writeUtf8(fileInfo.getAbsolutePath(), maskedContent);
                maskedFiles.add(new MaskedFileResult(fileInfo.getRepoRelativePath(), maskedContent));
            }
        }

        maskedFiles.sort(Comparator.comparing(MaskedFileResult::repoRelativePath));
        return maskedFiles;
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

    private static void printDetail(String label, String value) {
        System.out.printf("    %-25s: %s%n", label, value);
    }

    private static void printCategoryCount(String category, int count) {
        System.out.printf("    %-10s: %d%n", category, count);
    }

    private void printSummary(
            List<MaskedFileResult> maskedFiles,
            MappingRegistry mappingRegistry,
            int placeholdersReplaced) {
        System.out.println("==================================================");
        System.out.println("SUMMARY");
        System.out.println("==================================================");
        printMetric("Files Processed", pipelineFilesProcessed);
        printMetric("Archives Expanded", pipelineArchivesExpanded);
        printMetric("Files Masked", maskedFiles.size());
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

    private record MaskedFileResult(String repoRelativePath, String maskedContent) {
    }
}
