package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import org.opensearch.OpenSearchStatusException
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest
import org.opensearch.action.admin.indices.alias.get.GetAliasesRequest
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest
import org.opensearch.action.get.GetRequest
import org.opensearch.action.get.GetResponse
import org.opensearch.client.RequestOptions
import org.opensearch.client.RestHighLevelClient
import org.opensearch.client.core.CountRequest
import org.opensearch.client.indices.CreateIndexRequest
import org.opensearch.client.indices.DeleteAliasRequest
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
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
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

  fun count(index: SyncIndex) = try {
    client.count(CountRequest(index.indexName), RequestOptions.DEFAULT).count
  } catch (e: OpenSearchStatusException) {
    // if the index doesn't exist yet then we will get an exception, so catch and move on
    -1
  }

  fun save(prisoner: Prisoner, index: SyncIndex) {
    openSearchRestTemplate.index(IndexQueryBuilder().withObject(prisoner).build(), index.toIndexCoordinates())
  }

  fun getSummary(prisonerNumber: String, index: SyncIndex): PrisonerDocumentSummary? = client.get(GetRequest(index.indexName, prisonerNumber), RequestOptions.DEFAULT)
    .toPrisonerDocumentSummary(prisonerNumber)

  fun createPrisoner(prisoner: Prisoner, index: SyncIndex) {
    val response = openSearchRestTemplate.index(
      IndexQueryBuilder()
        .withObject(prisoner)
        .withOpType(IndexQuery.OpType.CREATE)
        .build(),
      index.toIndexCoordinates(),
    )
    if (response != prisoner.prisonerNumber) {
      throw IllegalStateException("Unexpected result $response from create of ${prisoner.prisonerNumber}")
    }
  }

  fun updatePrisoner(
    prisonerNumber: String,
    prisoner: Prisoner,
    index: SyncIndex,
    summary: PrisonerDocumentSummary,
  ): Boolean {
    val prisonerMap = objectMapperWithNulls.convertValue<Map<String, *>>(prisoner).toMutableMap()

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

    val response = openSearchRestTemplate.update(
      UpdateQuery.builder(prisonerNumber)
        .withDocument(Document.from(prisonerMap))
        .withIfSeqNo(summary.sequenceNumber)
        .withIfPrimaryTerm(summary.primaryTerm)
        .build(),
      index.toIndexCoordinates(),
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

  fun updateIncentive(
    prisonerNumber: String,
    incentive: CurrentIncentive?,
    index: SyncIndex,
    summary: PrisonerDocumentSummary,
  ): Boolean {
    val incentiveMap = mapOf(
      "currentIncentive" to (incentive?.let { objectMapperWithNulls.convertValue<Map<String, *>>(incentive) }),
    )
    val response = openSearchRestTemplate.update(
      UpdateQuery.builder(prisonerNumber)
        .withDocument(Document.from(incentiveMap))
        .withIfSeqNo(summary.sequenceNumber)
        .withIfPrimaryTerm(summary.primaryTerm)
        .build(),
      index.toIndexCoordinates(),
    )

    return when (response.result) {
      UpdateResponse.Result.NOOP -> false
      UpdateResponse.Result.UPDATED -> true

      UpdateResponse.Result.CREATED,
      UpdateResponse.Result.DELETED,
      UpdateResponse.Result.NOT_FOUND,
      null,
      -> throw IllegalStateException("Unexpected result ${response.result} from incentive update of $prisonerNumber")
    }
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
    index: SyncIndex,
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
    val response = openSearchRestTemplate.update(
      UpdateQuery.builder(prisonerNumber)
        .withDocument(Document.from(map))
        .withIfSeqNo(summary.sequenceNumber)
        .withIfPrimaryTerm(summary.primaryTerm)
        .build(),
      index.toIndexCoordinates(),
    )

    return when (response.result) {
      UpdateResponse.Result.NOOP -> false
      UpdateResponse.Result.UPDATED -> true

      UpdateResponse.Result.CREATED,
      UpdateResponse.Result.DELETED,
      UpdateResponse.Result.NOT_FOUND,
      null,
      -> throw IllegalStateException("Unexpected result ${response.result} from restricted patient update of $prisonerNumber")
    }
  }

  fun delete(prisonerNumber: String, index: SyncIndex) {
    openSearchRestTemplate.delete(prisonerNumber, index.toIndexCoordinates())
  }

  fun get(prisonerNumber: String, indices: List<SyncIndex>): Prisoner? = indices.firstNotNullOfOrNull {
    openSearchRestTemplate.get(prisonerNumber, Prisoner::class.java, it.toIndexCoordinates())
  }

  fun createIndex(index: SyncIndex) {
    log.info("creating index {}", index.indexName)
    client.indices().create(CreateIndexRequest(index.indexName), RequestOptions.DEFAULT)
    addMapping(index)
  }

  fun addMapping(index: SyncIndex) {
    log.info("adding mapping to index {}", index.indexName)
    openSearchRestTemplate.indexOps(IndexCoordinates.of(index.indexName)).apply {
      putMapping(createMapping(Prisoner::class.java))
    }
  }

  fun deleteIndex(index: SyncIndex) {
    log.info("deleting index {}", index.indexName)
    if (client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)) {
      client.indices().delete(DeleteIndexRequest(index.indexName), RequestOptions.DEFAULT)
    } else {
      log.warn("index {} was never there in the first place", index.indexName)
    }
  }

  fun doesIndexExist(index: SyncIndex): Boolean = client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)

  fun switchAliasIndex(index: SyncIndex) {
    val alias = client.indices().getAlias(GetAliasesRequest().aliases(PRISONER_INDEX), RequestOptions.DEFAULT)
    client.indices()
      .updateAliases(
        IndicesAliasesRequest().addAliasAction(
          IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD).index(index.indexName)
            .alias(PRISONER_INDEX),
        ),
        RequestOptions.DEFAULT,
      )

    alias.aliases[index.otherIndex().indexName]?.forEach {
      client.indices()
        .deleteAlias(DeleteAliasRequest(index.otherIndex().indexName, it.alias), RequestOptions.DEFAULT)
    }
  }

  fun prisonerAliasIsPointingAt(): Set<String> {
    val alias = client.indices().getAlias(GetAliasesRequest().aliases(PRISONER_INDEX), RequestOptions.DEFAULT)
    return alias.aliases.keys
  }

  fun copyPrisoner(prisoner: Prisoner): Prisoner = objectMapper.readValue(objectMapper.writeValueAsString(prisoner), Prisoner::class.java)

  private fun GetResponse.toPrisonerDocumentSummary(prisonerNumber: String): PrisonerDocumentSummary? = source?.let {
    PrisonerDocumentSummary(
      prisonerNumber,
      objectMapper.convertValue(source, Prisoner::class.java),
      seqNo.toInt(),
      primaryTerm.toInt(),
    )
  }
}

data class PrisonerDocumentSummary(
  val prisonerNumber: String?,
  val prisoner: Prisoner?,
  val sequenceNumber: Int,
  val primaryTerm: Int,
)

private fun SyncIndex.toIndexCoordinates() = IndexCoordinates.of(this.indexName)
