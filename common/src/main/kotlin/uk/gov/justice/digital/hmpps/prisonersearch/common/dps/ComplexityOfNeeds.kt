package uk.gov.justice.digital.hmpps.prisonersearch.common.dps

data class ComplexityOfNeeds(
  val offenderNo: String,
  val level: String,
//  val sourceUser": "JSMITH_GEN",
//  val sourceSystem": "hmpps-api-client-id",
//  val notes": "string",
//  val createdTimeStamp": "2021-03-02T17:18:46.457Z",
//  val updatedTimeStamp": "2021-03-02T17:18:46.457Z",
  val active: Boolean // ignoring this for now as the existing KW functionality doesn't use it
)
