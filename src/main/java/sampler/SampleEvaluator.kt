package sampler

import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*

class SampleEvaluator(private val sampleDirectory: File) {

    fun printStatistics() {
        val sampleFiles = filesGroupedByChecksum()

        printChecksumStatistics(sampleFiles)
        LOGGER.info("")
        LOGGER.info("")
        LOGGER.info("")

        printWorkItemCount(sampleFiles)
        LOGGER.info("")
        LOGGER.info("")
        LOGGER.info("")

        printWorkItemStatistics(sampleFiles)
        LOGGER.info("")
        LOGGER.info("")
        LOGGER.info("")

        printDuplicateWorkItemStatistics(sampleFiles)
    }

    private fun filesGroupedByChecksum(): List<SampleFile> {
        val csvFiles = sampleDirectory.listFiles { _, name -> name.endsWith("csv") } ?: arrayOf<File>()
        return csvFiles.asSequence()
            .groupBy { it.name.substringBefore("-") /* checksum */ }
            .map { (checksum, files) -> SampleFile(checksum, files) }
    }

    private fun printWorkItemCount(sampleFiles: List<SampleFile>) {
        LOGGER.info("Total WorkItems in Sample File")
        LOGGER.info("| %64s | %s |".format("Checksum", "Number of WorkItems"))
        sampleFiles
            .sortedByDescending { it.numSamplesWithSameChecksum }
            .map { sampleFile ->
                val numWorkItems = sampleFile.data.asSequence()
                    .map { it[2] /* work item id */ }
                    .count()
                LOGGER.info("| %64s | %19d |".format(sampleFile.checksum, numWorkItems))
            }

    }

    private fun printWorkItemStatistics(sampleFiles: List<SampleFile>) {
        LOGGER.info("WorkItems presence in samples")

        val checksumsInOrderBySampleCount = sampleFiles.sortedByDescending { it.numSamplesWithSameChecksum }


        LOGGER.info("| Work Item Id | ${checksumsInOrderBySampleCount.joinToString(" | ") { "${it.checksum.take(5)}..." }} |")
        sampleFiles
            .flatMap { sampleFile ->
                sampleFile.data.map { it[2] /* work item id */ }
            }
            .distinct()
            .sorted()
            .forEach { workItemId ->
                val presenceInSampleFiles = sampleFiles
                    .sortedByDescending { it.numSamplesWithSameChecksum }
                    .map { if (it.data.any { row -> row[2] == workItemId }) "✓" else "missing" }
                    .joinToString(" | ") { "%8s".format(it) }
                LOGGER.info("| %12s | $presenceInSampleFiles |".format(workItemId))
            }
    }

    private fun printChecksumStatistics(sampleFiles: List<SampleFile>) {
        LOGGER.info("Results by same response")
        LOGGER.info("| %64s | %s |".format("Checksum", "Number of identical Samples"))
        sampleFiles
            .sortedByDescending { it.numSamplesWithSameChecksum }
            .forEach {
                LOGGER.info("| ${it.checksum} | %27d |".format(it.numSamplesWithSameChecksum))
            }
    }

    private fun printDuplicateWorkItemStatistics(sampleFiles: List<SampleFile>) {

        LOGGER.info("Responses containing duplicate WorkItems")
        sampleFiles
            .sortedByDescending { it.numSamplesWithSameChecksum }
            .forEach { sampleFile ->
                LOGGER.info("Files with checksum ${sampleFile.checksum}")

                val duplicates = sampleFile.data.asSequence()
                    .map {
                        val workItemId = it[2]
                        val pageId = it[0]
                        Pair(workItemId, pageId.toInt())
                    }
                    .groupBy { it.first }
                    .filter { it.value.size > 1 }

                if (duplicates.isEmpty()) {
                    LOGGER.info("No duplicates present")
                } else {
                    LOGGER.info("| %15s | %s | %s |".format("WorkItem Id", "Times present in Sample", "Received in requests"))
                    duplicates.forEach { (workItemId, pageIds) ->
                        LOGGER.info("| %15s | %23d | %20s |".format(workItemId, pageIds.size, pageIds.map { it.second }.sorted().joinToString()))
                    }
                }

                LOGGER.info("")
            }
    }

    internal data class SampleFile(
        internal val checksum: String,
        internal val files: List<File>
    ) {
        internal val numSamplesWithSameChecksum: Int = files.size

        private val lines: List<String> by lazy {
            files.first().readLines(StandardCharsets.UTF_8)
        }

        internal val data: List<List<String>> by lazy {
            lines
                .drop(1) // headline
                .map { it.split(";") }
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(SampleEvaluator::class.java)
    }

}

fun main() {
    val properties = Properties().also {
        it.load(SampleDownloader::class.java.getResourceAsStream("/sampler.properties"))
    }
    require(properties.isNotEmpty()) { "sampler.properties file not found" }

    SampleEvaluator(File(properties.getProperty("sampler.target.dir"))).printStatistics()
}