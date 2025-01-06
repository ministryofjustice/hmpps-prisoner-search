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
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder
import org.springframework.data.elasticsearch.core.query.ScriptType
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

  val objectMapperWithNulls = objectMapper.copy().apply {
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

  fun getSummary(prisonerNumber: String, index: SyncIndex): PrisonerDocumentSummary? =
    client.get(GetRequest(index.indexName, prisonerNumber), RequestOptions.DEFAULT)
      .toPrisonerDocumentSummary(prisonerNumber)

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

  fun delete(prisonerNumber: String) {
    listOf(SyncIndex.GREEN, SyncIndex.BLUE).forEach {
      openSearchRestTemplate.delete(prisonerNumber, it.toIndexCoordinates())
    }
  }

  fun delete(prisonerNumber: String, index: SyncIndex) {
    openSearchRestTemplate.delete(prisonerNumber, index.toIndexCoordinates())
  }

  fun get(prisonerNumber: String, indices: List<SyncIndex>): Prisoner? =
    indices.firstNotNullOfOrNull {
      openSearchRestTemplate.get(prisonerNumber, Prisoner::class.java, it.toIndexCoordinates())
    }

  fun getRaw(prisonerNumber: String, index: SyncIndex): GetResponse =
    client.get(GetRequest(index.indexName, prisonerNumber), RequestOptions.DEFAULT)

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

  fun updatePrisonerScript(
    prisonerNumber: String,
    prisoner: Prisoner,
    index: SyncIndex,
    summary: PrisonerDocumentSummary,
  ) {
    val prisonerMap = objectMapper.convertValue<Map<String, *>>(prisoner)
    openSearchRestTemplate.update(
      UpdateQuery.builder(prisonerNumber)
        .withScriptType(ScriptType.INLINE)
        .withScript(
          """
            |ctx._source.pncNumber = params.pncNumber;
            |ctx._source.pncNumberCanonicalShort = params.pncNumberCanonicalShort;
            |ctx._source.pncNumberCanonicalLong = params.pncNumberCanonicalLong;
            |ctx._source.prisonerNumber = params.prisonerNumber;
            |ctx._source.pncNumber = params.pncNumber;
            |ctx._source.pncNumberCanonicalShort = params.pncNumberCanonicalShort;
            |ctx._source.pncNumberCanonicalLong = params.pncNumberCanonicalLong;
            |ctx._source.croNumber = params.croNumber;
            |ctx._source.bookingId = params.bookingId;
            |ctx._source.bookNumber = params.bookNumber;
            |ctx._source.title = params.title;
            |ctx._source.firstName = params.firstName;
            |ctx._source.middleNames = params.middleNames;
            |ctx._source.lastName = params.lastName;
            |ctx._source.dateOfBirth = params.dateOfBirth;
            |ctx._source.gender = params.gender;
            |ctx._source.ethnicity = params.ethnicity;
            |ctx._source.raceCode = params.raceCode;
            |ctx._source.youthOffender = params.youthOffender;
            |ctx._source.maritalStatus = params.maritalStatus;
            |ctx._source.religion = params.religion;
            |ctx._source.nationality = params.nationality;
            |ctx._source.status = params.status;
            |ctx._source.lastMovementTypeCode = params.lastMovementTypeCode;
            |ctx._source.lastMovementReasonCode = params.lastMovementReasonCode;
            |ctx._source.inOutStatus = params.inOutStatus;
            |ctx._source.prisonId = params.prisonId;
            |ctx._source.lastPrisonId = params.lastPrisonId;
            |ctx._source.prisonName = params.prisonName;
            |ctx._source.cellLocation = params.cellLocation;
            |ctx._source.aliases = params.aliases;
            |ctx._source.alerts = params.alerts;
            |ctx._source.csra = params.csra;
            |ctx._source.category = params.category;
            |ctx._source.legalStatus = params.legalStatus;
            |ctx._source.imprisonmentStatus = params.imprisonmentStatus;
            |ctx._source.imprisonmentStatusDescription = params.imprisonmentStatusDescription;
            |ctx._source.mostSeriousOffence = params.mostSeriousOffence;
            |ctx._source.recall = params.recall;
            |ctx._source.indeterminateSentence = params.indeterminateSentence;
            |ctx._source.sentenceStartDate = params.sentenceStartDate;
            |ctx._source.releaseDate = params.releaseDate;
            |ctx._source.confirmedReleaseDate = params.confirmedReleaseDate;
            |ctx._source.sentenceExpiryDate = params.sentenceExpiryDate;
            |ctx._source.licenceExpiryDate = params.licenceExpiryDate;
            |ctx._source.homeDetentionCurfewEligibilityDate = params.homeDetentionCurfewEligibilityDate;
            |ctx._source.homeDetentionCurfewActualDate = params.homeDetentionCurfewActualDate;
            |ctx._source.homeDetentionCurfewEndDate = params.homeDetentionCurfewEndDate;
            |ctx._source.topupSupervisionStartDate = params.topupSupervisionStartDate;
            |ctx._source.topupSupervisionExpiryDate = params.topupSupervisionExpiryDate;
            |ctx._source.additionalDaysAwarded = params.additionalDaysAwarded;
            |ctx._source.nonDtoReleaseDate = params.nonDtoReleaseDate;
            |ctx._source.nonDtoReleaseDateType = params.nonDtoReleaseDateType;
            |ctx._source.receptionDate = params.receptionDate;
            |ctx._source.paroleEligibilityDate = params.paroleEligibilityDate;
            |ctx._source.automaticReleaseDate = params.automaticReleaseDate;
            |ctx._source.postRecallReleaseDate = params.postRecallReleaseDate;
            |ctx._source.conditionalReleaseDate = params.conditionalReleaseDate;
            |ctx._source.actualParoleDate = params.actualParoleDate;
            |ctx._source.tariffDate = params.tariffDate;
            |ctx._source.releaseOnTemporaryLicenceDate = params.releaseOnTemporaryLicenceDate;
            |ctx._source.locationDescription = params.locationDescription;
            |ctx._source.restrictedPatient = params.restrictedPatient;
            |ctx._source.supportingPrisonId = params.supportingPrisonId;
            |ctx._source.dischargedHospitalId = params.dischargedHospitalId;
            |ctx._source.dischargedHospitalDescription = params.dischargedHospitalDescription;
            |ctx._source.dischargeDate = params.dischargeDate;
            |ctx._source.dischargeDetails = params.dischargeDetails;
            |ctx._source.currentIncentive = params.currentIncentive;
            |ctx._source.heightCentimetres = params.heightCentimetres;
            |ctx._source.weightKilograms = params.weightKilograms;
            |ctx._source.hairColour = params.hairColour;
            |ctx._source.rightEyeColour = params.rightEyeColour;
            |ctx._source.leftEyeColour = params.leftEyeColour;
            |ctx._source.facialHair = params.facialHair;
            |ctx._source.shapeOfFace = params.shapeOfFace;
            |ctx._source.build = params.build;
            |ctx._source.shoeSize = params.shoeSize;
            |ctx._source.tattoos = params.tattoos;
            |ctx._source.scars = params.scars;
            |ctx._source.marks = params.marks;
            |ctx._source.addresses = params.addresses;
            |ctx._source.emailAddresses = params.emailAddresses;
            |ctx._source.phoneNumbers = params.phoneNumbers;
            |ctx._source.identifiers = params.identifiers;
            |ctx._source.allConvictedOffences = params.allConvictedOffences
          """.trimMargin(),
        )
        .withParams(
          mapOf(
            "prisonerNumber" to prisonerMap["prisonerNumber"],
            "pncNumber" to prisonerMap["pncNumber"],
            "pncNumberCanonicalShort" to prisonerMap["pncNumberCanonicalShort"],
            "pncNumberCanonicalLong" to prisonerMap["pncNumberCanonicalLong"],
            "croNumber" to prisonerMap["croNumber"],
            "bookingId" to prisonerMap["bookingId"],
            "bookNumber" to prisonerMap["bookNumber"],
            "title" to prisonerMap["title"],
            "firstName" to prisonerMap["firstName"],
            "middleNames" to prisonerMap["middleNames"],
            "lastName" to prisonerMap["lastName"],
            "dateOfBirth" to prisonerMap["dateOfBirth"],
            "gender" to prisonerMap["gender"],
            "ethnicity" to prisonerMap["ethnicity"],
            "raceCode" to prisonerMap["raceCode"],
            "youthOffender" to prisonerMap["youthOffender"],
            "maritalStatus" to prisonerMap["maritalStatus"],
            "religion" to prisonerMap["religion"],
            "nationality" to prisonerMap["nationality"],
            "status" to prisonerMap["status"],
            "lastMovementTypeCode" to prisonerMap["lastMovementTypeCode"],
            "lastMovementReasonCode" to prisonerMap["lastMovementReasonCode"],
            "inOutStatus" to prisonerMap["inOutStatus"],
            "prisonId" to prisonerMap["prisonId"],
            "lastPrisonId" to prisonerMap["lastPrisonId"],
            "prisonName" to prisonerMap["prisonName"],
            "cellLocation" to prisonerMap["cellLocation"],
            "aliases" to prisonerMap["aliases"],
            "alerts" to prisonerMap["alerts"],
            "csra" to prisonerMap["csra"],
            "category" to prisonerMap["category"],
            "legalStatus" to prisonerMap["legalStatus"],
            "imprisonmentStatus" to prisonerMap["imprisonmentStatus"],
            "imprisonmentStatusDescription" to prisonerMap["imprisonmentStatusDescription"],
            "mostSeriousOffence" to prisonerMap["mostSeriousOffence"],
            "recall" to prisonerMap["recall"],
            "indeterminateSentence" to prisonerMap["indeterminateSentence"],
            "sentenceStartDate" to prisonerMap["sentenceStartDate"],
            "releaseDate" to prisonerMap["releaseDate"],
            "confirmedReleaseDate" to prisonerMap["confirmedReleaseDate"],
            "sentenceExpiryDate" to prisonerMap["sentenceExpiryDate"],
            "licenceExpiryDate" to prisonerMap["licenceExpiryDate"],
            "homeDetentionCurfewEligibilityDate" to prisonerMap["homeDetentionCurfewEligibilityDate"],
            "homeDetentionCurfewActualDate" to prisonerMap["homeDetentionCurfewActualDate"],
            "homeDetentionCurfewEndDate" to prisonerMap["homeDetentionCurfewEndDate"],
            "topupSupervisionStartDate" to prisonerMap["topupSupervisionStartDate"],
            "topupSupervisionExpiryDate" to prisonerMap["topupSupervisionExpiryDate"],
            "additionalDaysAwarded" to prisonerMap["additionalDaysAwarded"],
            "nonDtoReleaseDate" to prisonerMap["nonDtoReleaseDate"],
            "nonDtoReleaseDateType" to prisonerMap["nonDtoReleaseDateType"],
            "receptionDate" to prisonerMap["receptionDate"],
            "paroleEligibilityDate" to prisonerMap["paroleEligibilityDate"],
            "automaticReleaseDate" to prisonerMap["automaticReleaseDate"],
            "postRecallReleaseDate" to prisonerMap["postRecallReleaseDate"],
            "conditionalReleaseDate" to prisonerMap["conditionalReleaseDate"],
            "actualParoleDate" to prisonerMap["actualParoleDate"],
            "tariffDate" to prisonerMap["tariffDate"],
            "releaseOnTemporaryLicenceDate" to prisonerMap["releaseOnTemporaryLicenceDate"],
            "locationDescription" to prisonerMap["locationDescription"],
            "restrictedPatient" to prisonerMap["restrictedPatient"],
            "supportingPrisonId" to prisonerMap["supportingPrisonId"],
            "dischargedHospitalId" to prisonerMap["dischargedHospitalId"],
            "dischargedHospitalDescription" to prisonerMap["dischargedHospitalDescription"],
            "dischargeDate" to prisonerMap["dischargeDate"],
            "dischargeDetails" to prisonerMap["dischargeDetails"],
            "currentIncentive" to prisonerMap["currentIncentive"],
            "heightCentimetres" to prisonerMap["heightCentimetres"],
            "weightKilograms" to prisonerMap["weightKilograms"],
            "hairColour" to prisonerMap["hairColour"],
            "rightEyeColour" to prisonerMap["rightEyeColour"],
            "leftEyeColour" to prisonerMap["leftEyeColour"],
            "facialHair" to prisonerMap["facialHair"],
            "shapeOfFace" to prisonerMap["shapeOfFace"],
            "build" to prisonerMap["build"],
            "shoeSize" to prisonerMap["shoeSize"],
            "tattoos" to prisonerMap["tattoos"],
            "scars" to prisonerMap["scars"],
            "marks" to prisonerMap["marks"],
            "addresses" to prisonerMap["addresses"],
            "emailAddresses" to prisonerMap["emailAddresses"],
            "phoneNumbers" to prisonerMap["phoneNumbers"],
            "identifiers" to prisonerMap["identifiers"],
            "allConvictedOffences" to prisonerMap["allConvictedOffences"],
          ),
        )
        .withLang("painless")
        .withIfSeqNo(summary.sequenceNumber)
        .withIfPrimaryTerm(summary.primaryTerm)
        .build(),
      index.toIndexCoordinates(),
    )
  }

  fun copyPrisoner(prisoner: Prisoner): Prisoner =
    objectMapper.readValue(objectMapper.writeValueAsString(prisoner), Prisoner::class.java)

  private fun GetResponse.toPrisonerDocumentSummary(prisonerNumber: String): PrisonerDocumentSummary? =
    source?.let {
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
