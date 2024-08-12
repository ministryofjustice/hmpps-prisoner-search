package uk.gov.justice.digital.hmpps.prisonersearch.indexer.repository

import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.data.elasticsearch.core.query.ScriptType
import org.springframework.data.elasticsearch.core.query.UpdateQuery
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonersearch.common.config.OpenSearchIndexConfiguration.Companion.PRISONER_INDEX
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.CurrentIncentive
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonersearch.common.model.SyncIndex
import java.time.format.DateTimeFormatter

@Repository
class PrisonerRepository(
  private val client: RestHighLevelClient,
  private val openSearchRestTemplate: ElasticsearchOperations,
  private val objectMapper: ObjectMapper,
) {
  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val DATE_TIME_PATTERN = DateTimeFormatter.ofPattern("YYYY-MM-dd'T'HH:mm:ss")
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

  fun getSummary(prisonerNumber: String, indices: List<SyncIndex>): PrisonerDocumentSummary? =
    indices.firstNotNullOfOrNull { index ->
      client.get(GetRequest(index.indexName, prisonerNumber), RequestOptions.DEFAULT)
        .toPrisonerDocumentSummary(prisonerNumber)
    }

  fun updateIncentive(
    prisonerNumber: String,
    incentive: CurrentIncentive?,
    index: SyncIndex,
    summary: PrisonerDocumentSummary,
  ) {
    incentive?.run {
      openSearchRestTemplate.update(
        UpdateQuery.builder(prisonerNumber)
          .withScriptType(ScriptType.INLINE)
          .withScript(
            "ctx._source.currentIncentive = ['level':['code':params.code, 'description':params.description], 'dateTime':params.dateTime, 'nextReviewDate':params.nextReviewDate]",
          )
          .withParams(
            mapOf(
              "code" to incentive.level.code,
              "description" to incentive.level.description,
              "dateTime" to incentive.dateTime.format(DATE_TIME_PATTERN),
              "nextReviewDate" to incentive.nextReviewDate,
            ),
          )
          .withLang("painless")
          .withIfSeqNo(summary.sequenceNumber)
          .withIfPrimaryTerm(summary.primaryTerm)
          .build(),
        index.toIndexCoordinates(),
      )
    } ?: run {
      openSearchRestTemplate.update(
        UpdateQuery.builder(prisonerNumber)
          .withScriptType(ScriptType.INLINE)
          .withScript("ctx._source.currentIncentive = null")
          .withLang("painless")
          .withIfSeqNo(summary.sequenceNumber)
          .withIfPrimaryTerm(summary.primaryTerm)
          .build(),
        index.toIndexCoordinates(),
      )
    }
  }

  fun delete(prisonerNumber: String) {
    listOf(SyncIndex.GREEN, SyncIndex.BLUE).forEach {
      openSearchRestTemplate.delete(prisonerNumber, it.toIndexCoordinates())
    }
  }

  fun get(prisonerNumber: String, indices: List<SyncIndex>): Prisoner? =
    indices.firstNotNullOfOrNull {
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

  fun doesIndexExist(index: SyncIndex): Boolean =
    client.indices().exists(GetIndexRequest(index.indexName), RequestOptions.DEFAULT)

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

  // @Suppress("UNCHECKED_CAST")
  private fun GetResponse.toPrisonerDocumentSummary(prisonerNumber: String): PrisonerDocumentSummary? =
    source ?. let {
      PrisonerDocumentSummary(
        prisonerNumber,
        source["bookingId"]?.toString()?.toLong(),
        objectMapper.convertValue(source["currentIncentive"], CurrentIncentive::class.java),
        seqNo.toInt(),
        primaryTerm.toInt(),
      )
    }
}

data class PrisonerDocumentSummary(
  val prisonerNumber: String?,
  val bookingId: Long?,
  val currentIncentive: CurrentIncentive?,
  // val prisoner: Prisoner?,
  val sequenceNumber: Int,
  val primaryTerm: Int,
)

private fun SyncIndex.toIndexCoordinates() = IndexCoordinates.of(this.indexName)
