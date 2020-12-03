package sampler

import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun main() {
    val properties = Properties().also {
        it.load(SampleDownloader::class.java.getResourceAsStream("/sampler.properties"))
    }
    require(properties.isNotEmpty()) { "sampler.properties file not found" }

    val numParallelThreads = properties.getProperty("sampler.threads").toInt()
    val numSamples = properties.getProperty("sampler.sample.count").toInt()

    val sampleAndLogDirectory = File(properties.getProperty("sampler.target.dir")).also { it.mkdirs() }

    val ytService = YouTrackService(URI(properties.getProperty("youtrack.api.url")), properties.getProperty("youtrack.permanent.token"))
    val executor = Executors.newFixedThreadPool(numParallelThreads)

    LoggerFactory.getLogger("Starter").info("Fetching $numSamples Samples in $numParallelThreads Threads")
    val startDate: LocalDate = LocalDate.parse(properties.getProperty("sampler.date.start"), DateTimeFormatter.ISO_DATE)
    val endDate: LocalDate = LocalDate.parse(properties.getProperty("sampler.date.end"), DateTimeFormatter.ISO_DATE)
    SampleDownloader(ytService, executor, numSamples).download(startDate, endDate, sampleAndLogDirectory)
    executor.shutdown()
    executor.awaitTermination(30, TimeUnit.MINUTES)

    // print statistics
    SampleEvaluator(sampleAndLogDirectory).printStatistics()
}
