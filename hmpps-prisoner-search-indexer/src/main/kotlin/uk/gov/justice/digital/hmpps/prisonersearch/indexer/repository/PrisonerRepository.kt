package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import org.opensearch.OpenSearchStatusException
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest
import org.opensearch.action.get.GetRequest
import org.opensearch.action.get.GetResponse
import org.opensearch.client.Request
import org.opensearch.client.RequestOptions
import org.opensearch.client.ResponseException
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.GetIndexRequest
import org.slf4j.LoggerFactory
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.document.Document
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQuery
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.data.elasticsearch.core.query.UpdateQuery
import org.springframework.data.elasticsearch.core.query.UpdateResponse
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration.Companion.PRISONER_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.PrisonerAlert
import java.time.LocalDate

@Repository
class PrisonerRepository(
  private val client: RestHighLevelClient,
  private val openSearchRestTemplate: ElasticsearchOperations,
  private val objectMapper: ObjectMapper,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  val objectMapperWithNulls: ObjectMapper = objectMapper.copy().apply {
    setSerializationInclusion(JsonInclude.Include.ALWAYS)
  }

  fun count() = try {
    client.count(CountRequest(PRISONER_INDEX), RequestOptions.DEFAULT).count
  } catch (_: OpenSearchStatusException) {
    // if the index doesn't exist yet then we will get an exception, so catch and move on
    -1
  }

  fun save(prisoner: Prisoner) {
    openSearchRestTemplate.index(IndexQueryBuilder().withObject(prisoner).build(), IndexCoordinates.of(PRISONER_INDEX))
  }

  fun getSummary(prisonerNumber: String): PrisonerDocumentSummary? = client
    .get(GetRequest(PRISONER_INDEX, prisonerNumber), RequestOptions.DEFAULT)
    .toPrisonerDocumentSummary(prisonerNumber)

  fun createPrisoner(prisoner: Prisoner) {
    val response = openSearchRestTemplate.index(
      IndexQueryBuilder()
        .withObject(prisoner)
        .withOpType(IndexQuery.OpType.CREATE)
        .build(),
      IndexCoordinates.of(PRISONER_INDEX),
    )
    if (response != prisoner.prisonerNumber) {
      throw IllegalStateException("Unexpected result $response from create of ${prisoner.prisonerNumber}")
    }
  }

  fun updatePrisoner(
    prisonerNumber: String,
    prisoner: Prisoner,
    summary: PrisonerDocumentSummary,
  ): Boolean {
    val prisonerMap = objectMapperWithNulls.convertValue<Map<String, *>>(prisoner).toMutableMap()

    removeDomainFields(prisonerMap, summary)

    return doUpdate(prisonerNumber, prisonerMap, summary)
  }

  fun updateIncentive(
    prisonerNumber: String,
    incentive: CurrentIncentive?,
    summary: PrisonerDocumentSummary,
  ): Boolean {
    val incentiveMap = mapOf(
      "currentIncentive" to (incentive?.let { objectMapperWithNulls.convertValue<Map<String, *>>(incentive) }),
    )
    return doUpdate(prisonerNumber, incentiveMap, summary)
  }

  fun updateRestrictedPatient(
    prisonerNumber: String,
    restrictedPatient: Boolean,
    supportingPrisonId: String? = null,
    dischargedHospitalId: String? = null,
    dischargedHospitalDescription: String? = null,
    dischargeDate: LocalDate? = null,
    dischargeDetails: String? = null,
    locationDescription: String? = null,
    summary: PrisonerDocumentSummary,
  ): Boolean {
    val map = mapOf(
      "restrictedPatient" to restrictedPatient,
      "supportingPrisonId" to supportingPrisonId,
      "dischargedHospitalId" to dischargedHospitalId,
      "dischargedHospitalDescription" to dischargedHospitalDescription,
      "dischargeDate" to dischargeDate,
      "dischargeDetails" to dischargeDetails,
      "locationDescription" to locationDescription,
    )
    return doUpdate(prisonerNumber, map, summary)
  }

  fun updateAlerts(
    prisonerNumber: String,
    alerts: List<PrisonerAlert>?,
    summary: PrisonerDocumentSummary,
  ): Boolean = doUpdate(
    prisonerNumber,
    alerts?.let {
      mapOf("alerts" to objectMapperWithNulls.convertValue(alerts))
    } ?: mapOf("alerts" to null),
    summary,
  )

  fun updateComplexityOfNeed(
    prisonerNumber: String,
    complexityOfNeedLevel: String?,
    summary: PrisonerDocumentSummary,
  ): Boolean = doUpdate(
    prisonerNumber,
    mapOf("complexityOfNeedLevel" to complexityOfNeedLevel),
    summary,
  )

  private fun doUpdate(
    prisonerNumber: String,
    prisonerMap: Map<String, Any?>,
    summary: PrisonerDocumentSummary,
  ): Boolean {
    val response = openSearchRestTemplate.update(
      UpdateQuery.builder(prisonerNumber)
        .withDocument(Document.from(prisonerMap))
        .withIfSeqNo(summary.sequenceNumber)
        .withIfPrimaryTerm(summary.primaryTerm)
        .build(),
      IndexCoordinates.of(PRISONER_INDEX),
    )

    return when (response.result) {
      UpdateResponse.Result.NOOP -> false
      UpdateResponse.Result.UPDATED -> true

      UpdateResponse.Result.CREATED,
      UpdateResponse.Result.DELETED,
      UpdateResponse.Result.NOT_FOUND,
      null,
      -> throw IllegalStateException("Unexpected result ${response.result} from update of $prisonerNumber")
    }
  }

  fun delete(prisonerNumber: String): Boolean {
    val request = Request("DELETE", "/$PRISONER_INDEX/_doc/$prisonerNumber")
    try {
      client.lowLevelClient.performRequest(request)
      return true
    } catch (e: ResponseException) {
      log.error("Unexpected response ${e.response} from delete of $prisonerNumber", e)
      if (e.response.statusLine.statusCode == 404) {
        return false
      }
      throw e
    }
  }

  fun get(prisonerNumber: String): Prisoner? = openSearchRestTemplate
    .get(prisonerNumber, Prisoner::class.java, IndexCoordinates.of(PRISONER_INDEX))

  fun createIndex() {
    log.info("creating index")
    client.indices().create(CreateIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)
    addMapping()
  }

  fun addMapping() {
    log.info("adding mapping to index")
    openSearchRestTemplate.indexOps(IndexCoordinates.of(PRISONER_INDEX)).apply {
      putMapping(createMapping(Prisoner::class.java))
    }
  }

  fun deleteIndex() {
    log.info("deleting index")
    if (client.indices().exists(GetIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)) {
      client.indices().delete(DeleteIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)
    } else {
      log.warn("index {} was never there in the first place", PRISONER_INDEX)
    }
  }

  fun doesIndexExist(): Boolean = client.indices().exists(GetIndexRequest(PRISONER_INDEX), RequestOptions.DEFAULT)

  fun copyPrisoner(prisoner: Prisoner): Prisoner = objectMapper
    .readValue(objectMapper.writeValueAsString(prisoner), Prisoner::class.java)

  private fun GetResponse.toPrisonerDocumentSummary(prisonerNumber: String): PrisonerDocumentSummary? = source?.let {
    PrisonerDocumentSummary(
      prisonerNumber,
      objectMapper.convertValue(source, Prisoner::class.java),
      seqNo.toInt(),
      primaryTerm.toInt(),
    )
  }
}

private fun removeDomainFields(
  prisonerMap: MutableMap<String, Any?>,
  summary: PrisonerDocumentSummary,
) {
  prisonerMap.remove("currentIncentive")

  prisonerMap.remove("restrictedPatient")
  prisonerMap.remove("supportingPrisonId")
  prisonerMap.remove("dischargedHospitalId")
  prisonerMap.remove("dischargedHospitalDescription")
  prisonerMap.remove("dischargeDate")
  prisonerMap.remove("dischargeDetails")
  if (summary.prisoner?.restrictedPatient == true) {
    prisonerMap.remove("locationDescription")
  }

  prisonerMap.remove("alerts")
  prisonerMap.remove("complexityOfNeedLevel")
}

data class PrisonerDocumentSummary(
  val prisonerNumber: String?,
  val prisoner: Prisoner?,
  val sequenceNumber: Int,
  val primaryTerm: Int,
)
