package sampler

import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ExecutorService


class SampleDownloader(private val ytService: YouTrackService, private val executor: ExecutorService, private val numSamples: Int) {

    fun download(startDate: LocalDate, endDate: LocalDate, sampleAndLogDirectory: File) {
        repeat(numSamples) {
            executor.submit {
                val threadId = UUID.randomUUID().toString().takeLast(4)
                Thread.currentThread().name = threadId
                LOGGER.info("Starting")
                val workitemBatches = ytService.fetchWorkItems(startDate, endDate)

                LOGGER.info("${workitemBatches.size} WorkItem batches fetched")

                val csvContent = asCsvRows(workitemBatches)
                LOGGER.info("Generating Checksum")
                val checksum = generateChecksum(csvContent)

                val targetFile = File(sampleAndLogDirectory, "$checksum-$threadId-results.csv")
                LOGGER.info("Writing CSV to ${targetFile.absolutePath}")
                writeLines(csvContent, targetFile)
            }
        }
    }

    private fun asCsvRows(workitems: List<WorkItemBatch>): List<String> {
        return listOf("Request ID;Request Params;Work Item ID; Work Item CreateDate;Work Item UpdateDate;Work Item Date;Duration;") + workitems.flatMapIndexed { index, workItemBatch ->
            workItemBatch.workItems.map { workItem ->
                "$index;${workItemBatch.parametersUsed};${workItem.id};${workItem.createDate!!.format(DateTimeFormatter.ISO_DATE_TIME)};${workItem.updateDate?.format(DateTimeFormatter.ISO_DATE_TIME)};${workItem.date!!.format(DateTimeFormatter.ISO_DATE)};${workItem.duration}m;"
            }
        }
    }

    private fun generateChecksum(csvContent: List<String>): String {
        val combined = csvContent.joinToString("\n")
        val md = MessageDigest.getInstance("SHA-256") //SHA, MD2, MD5, SHA-256, SHA-384...
        val encodedData = md.digest(combined.toByteArray(StandardCharsets.UTF_8))

        val sb = StringBuilder()
        encodedData.forEach {
            sb.append(String.format("%02x", it))
        }

        return sb.toString();
    }

    private fun writeLines(workitems: List<String>, targetFile: File) {
        targetFile.createNewFile()
        FileOutputStream(targetFile).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
            workitems.forEach { line ->
                writer.write(line)
                writer.newLine()
            }

            writer.flush()
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(SampleDownloader::class.java)
    }

}




