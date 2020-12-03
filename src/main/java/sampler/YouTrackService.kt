package sampler

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.http.HttpHeaders
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class YouTrackService(private val endpoint: URI, private val permanentToken: String) {

    fun fetchWorkItems(startDate: LocalDate, endDate: LocalDate): List<WorkItemBatch> {
        val startDateFormattedForUrl = startDate.format(DATE_FORMATTER)
        val endDateFormattedForUrl = endDate.format(DATE_FORMATTER)

        var keepOnFetching = true
        val workItemBatches = mutableListOf<WorkItemBatch>()

        while (keepOnFetching) {
            val alreadyFetchedWorkItems = workItemBatches.flatMap { it.workItems }.count()
            val path = "/workItems?\$top=$MAX_WORKITEMS_PER_BATCH&\$skip=${alreadyFetchedWorkItems}&fields=$WORKITEM_FIELDS&startDate=$startDateFormattedForUrl&endDate=$endDateFormattedForUrl"
            val url = "${endpoint.toURL().toExternalForm()}$path"
            LOGGER.info("Fetching from $url")
            val getRequest = HttpGet(url)

            try {
                closeableHttpClient().use { client ->
                    client.execute(getRequest) { response ->
                        LOGGER.info("Got Response with Status ${response.statusLine.statusCode}")
                        if (response.statusLine.statusCode == 200) {
                            val workItems = OBJECT_MAPPER.readValue(response.entity.content, object : TypeReference<List<WorkItem>>() {})
                            workItemBatches.add(WorkItemBatch(path, workItems))

                            keepOnFetching = workItems.size == MAX_WORKITEMS_PER_BATCH
                        } else {
                            workItemBatches.add(WorkItemBatch(path, emptyList()))
                            LOGGER.warn("Error Response ${response.statusLine.reasonPhrase}")
                            LOGGER.warn("\\_ ${EntityUtils.toString(response.entity)}")
                        }
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Error fetching from $url. Trying same request again", e)
            }
        }

        return workItemBatches
    }

    private fun closeableHttpClient() = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig
                .custom()
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .build()
        )
        .setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        .setDefaultHeaders(
            listOf(
                BasicHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.118 Safari/537.36"),
                BasicHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, sdch"),
                BasicHeader(HttpHeaders.ACCEPT_LANGUAGE, "de-DE,de;q=0.8,en-US;q=0.6,en;q=0.4"),
                BasicHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*"),
                BasicHeader(HttpHeaders.AUTHORIZATION, "Bearer $permanentToken")
            )
        ).build()

    companion object {
        private val LOGGER = LoggerFactory.getLogger(YouTrackService::class.java.name)
        private val DATE_FORMATTER = DateTimeFormatter.ISO_DATE
        private val OBJECT_MAPPER = jacksonObjectMapper().findAndRegisterModules()
        private const val MAX_WORKITEMS_PER_BATCH = 400
        private const val USER_FIELDS = "id,login,fullName,email"
        private const val ISSUE_FIELDS = "idReadable,resolved,project(shortName,name),summary,wikifiedDescription,customFields(name,localizedName,aliases,value(name))"
        private const val WORKITEM_FIELDS = "id,author($USER_FIELDS),creator($USER_FIELDS),type(name),text,duration(minutes,presentation),date,created,updated,issue($ISSUE_FIELDS)"
    }
}

data class WorkItemBatch(
    val parametersUsed: String,
    val workItems: List<WorkItem>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkItem @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("author") val author: YouTrackUser?,
    @JsonProperty("creator") val creator: YouTrackUser?,
    @JsonProperty("text") val text: String?,
    @JsonProperty("type") val type: YouTrackWorkItemType?,
    @JsonProperty("duration") val duration: YouTrackWorkItemDuration,
    @JsonProperty("date") val dateTimestamp: Long?,
    @JsonProperty("created") val createdateTimestamp: Long?,
    @JsonProperty("updated") val updatedateTimestamp: Long?,
    @JsonProperty("issue") val issue: YouTrackIssue
) {

    val date: ZonedDateTime?
        @JsonIgnore get() = dateTimestamp?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        }

    val createDate: ZonedDateTime?
        @JsonIgnore get() = createdateTimestamp?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        }

    val updateDate: ZonedDateTime?
        @JsonIgnore get() = updatedateTimestamp?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTrackUser @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("login") val login: String?,
    @JsonProperty("fullName") val fullName: String?,
    @JsonProperty("email") val email: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTrackWorkItemType @JsonCreator constructor(
    @JsonProperty("name") val name: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class YouTrackWorkItemDuration @JsonCreator constructor(
    @JsonProperty("minutes") val minutes: Long,
    @JsonProperty("presentation") val presentation: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTrackIssue @JsonCreator constructor(
    @JsonProperty("idReadable") val id: String,
    @JsonProperty("project") val project: YouTrackProject?,
    @JsonProperty("resolved") val resolved: Long?,
    @JsonProperty("summary") val summary: String?,
    @JsonProperty("wikifiedDescription") val description: String?,
    @JsonProperty("customFields") val customFields: List<YouTrackCustomField>
) {

    val resolveDate: ZonedDateTime?
        get() = resolved?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        }

}

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTrackProject @JsonCreator constructor(
    @JsonProperty("name") val name: String?,
    @JsonProperty("shortName") val shortName: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTrackCustomField @JsonCreator constructor(
    @JsonProperty("name") val name: String?,
    @JsonProperty("value") val rawValue: JsonNode?
) {

    val values: List<YouTrackCustomFieldValue>
        @JsonIgnore get() = rawValue?.let { jsonNode ->
            return when {
                jsonNode.isArray -> readValues(jsonNode as ArrayNode)
                jsonNode.isNull -> emptyList()
                jsonNode.isObject -> listOf(readNodeValue(jsonNode))
                jsonNode.isNumber || jsonNode.isTextual || jsonNode.isBoolean -> listOf(YouTrackCustomFieldValue(jsonNode.textValue()))
                else -> {
                    LOGGER.warn("Unhandled YouTrackCustomField Value: ${jsonNode.nodeType}: $jsonNode. Defaulting to emptyList")
                    emptyList()
                }
            }
        } ?: emptyList()

    private fun readValues(arrayNode: ArrayNode): List<YouTrackCustomFieldValue> {
        return arrayNode.map { readNodeValue(it) }
    }

    private fun readNodeValue(node: JsonNode): YouTrackCustomFieldValue {
        val stringValue: String = node.toString()
        return jacksonObjectMapper().readValue(stringValue, YouTrackCustomFieldValue::class.java)
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(YouTrackCustomField::class.java.name)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class YouTrackCustomFieldValue @JsonCreator constructor(
    @JsonProperty("name") val value: String?
)