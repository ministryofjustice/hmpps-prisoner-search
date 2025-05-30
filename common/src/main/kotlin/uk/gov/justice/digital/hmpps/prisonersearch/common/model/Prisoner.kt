package uk.gov.justice.digital.hmpps.prisonersearch.common.model

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import org.apache.commons.lang3.builder.DiffResult
import org.apache.commons.lang3.builder.Diffable
import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.InnerField
import org.springframework.data.elasticsearch.annotations.MultiField
import java.time.LocalDate

@Document(indexName = "prisoner-search")
class Prisoner : Diffable<Prisoner> {
  @Id
  @Field(type = FieldType.Keyword)
  @Schema(required = true, description = "Prisoner Number", example = "A1234AA")
  var prisonerNumber: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "PNC Number", example = "12/394773H")
  @DiffableProperty(DiffCategory.IDENTIFIERS)
  var pncNumber: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "PNC Number", example = "12/394773H")
  var pncNumberCanonicalShort: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "PNC Number", example = "2012/394773H")
  var pncNumberCanonicalLong: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "CRO Number", example = "29906/12J")
  @DiffableProperty(DiffCategory.IDENTIFIERS)
  var croNumber: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "Booking No.", example = "0001200924")
  @DiffableProperty(DiffCategory.IDENTIFIERS)
  var bookingId: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "Book Number", example = "38412A")
  @DiffableProperty(DiffCategory.IDENTIFIERS)
  var bookNumber: String? = null

  @Schema(description = "Title", example = "Ms")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var title: String? = null

  @MultiField(
    mainField = Field(type = FieldType.Text),
    otherFields = [
      InnerField(type = FieldType.Keyword, suffix = "keyword"),
    ],
  )
  @Schema(required = true, description = "First Name", example = "Robert")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var firstName: String? = null

  @Schema(description = "Middle Names", example = "John James")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var middleNames: String? = null

  @MultiField(
    mainField = Field(type = FieldType.Text),
    otherFields = [
      InnerField(type = FieldType.Keyword, suffix = "keyword"),
    ],
  )
  @Schema(required = true, description = "Last name", example = "Larsen")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var lastName: String? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(required = true, description = "Date of Birth", example = "1975-04-02")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var dateOfBirth: LocalDate? = null

  @Schema(required = true, description = "Gender", example = "Female")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var gender: String? = null

  @Schema(required = true, description = "Ethnicity", example = "White: Eng./Welsh/Scot./N.Irish/British")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var ethnicity: String? = null

  @Schema(required = true, description = "Ethnicity code", example = "W1")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var raceCode: String? = null

  @Schema(required = true, description = "Youth Offender?", example = "true")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var youthOffender: Boolean? = null

  @Schema(required = true, description = "Marital Status", example = "Widowed")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var maritalStatus: String? = null

  @Schema(required = true, description = "Religion", example = "Church of England (Anglican)")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var religion: String? = null

  @Schema(required = true, description = "Nationality", example = "Egyptian")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var nationality: String? = null

  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  @Schema(description = "Smoker (V=vapes)", allowableValues = ["Y", "N", "V"])
  var smoker: String? = null

  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  @Schema(description = "Personal Care Needs")
  var personalCareNeeds: List<PersonalCareNeed>? = null

  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  @Schema(description = "Languages")
  var languages: List<Language>? = null

  // @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  @Schema(description = "The prisoner's current facial image can be retrieved by plugging this id into the prison-api endpoint /app/images/{prisonerNumber}/data?imageId={imageId}", example = "2122100")
  var currentFacialImageId: Long? = null

  @Field(type = FieldType.Keyword)
  @Schema(required = true, description = "Status of the prisoner", example = "ACTIVE IN")
  @DiffableProperty(DiffCategory.STATUS)
  var status: String? = null

  @Schema(description = "Last Movement Type Code of prisoner", example = "CRT")
  @DiffableProperty(DiffCategory.LOCATION)
  var lastMovementTypeCode: String? = null

  @Schema(description = "Last Movement Reason of prisoner", example = "CA")
  @DiffableProperty(DiffCategory.LOCATION)
  var lastMovementReasonCode: String? = null

  @Schema(description = "In/Out Status", example = "IN", allowableValues = ["IN", "OUT", "TRN"])
  @DiffableProperty(DiffCategory.STATUS)
  var inOutStatus: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "Prison ID", example = "MDI")
  @DiffableProperty(DiffCategory.LOCATION)
  var prisonId: String? = null

  @Field(type = FieldType.Keyword)
  @Schema(description = "The last prison for the prisoner (which is the same as the prisonId if they are still inside prison)", example = "MDI")
  @DiffableProperty(DiffCategory.LOCATION)
  var lastPrisonId: String? = null

  @Schema(description = "Prison Name", example = "HMP Leeds")
  @DiffableProperty(DiffCategory.LOCATION)
  var prisonName: String? = null

  @Schema(description = "In prison cell location", example = "A-1-002")
  @DiffableProperty(DiffCategory.LOCATION)
  var cellLocation: String? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "Aliases Names and Details")
  @DiffableProperty(DiffCategory.PERSONAL_DETAILS)
  var aliases: List<PrisonerAlias>? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "Alerts")
  @DiffableProperty(DiffCategory.ALERTS)
  var alerts: List<PrisonerAlert>? = null

  @Schema(description = "Cell Sharing Risk Assessment", example = "HIGH")
  @DiffableProperty(DiffCategory.STATUS)
  var csra: String? = null

  @Schema(description = "Prisoner Category", example = "C")
  @DiffableProperty(DiffCategory.STATUS)
  var category: String? = null

  @Schema(description = "Complexity of Need level if female", example = "low")
  @DiffableProperty(DiffCategory.STATUS)
  var complexityOfNeedLevel: String? = null

  @Schema(
    description = "Legal Status",
    example = "SENTENCED",
    allowableValues = ["RECALL", "DEAD", "INDETERMINATE_SENTENCE", "SENTENCED", "CONVICTED_UNSENTENCED", "CIVIL_PRISONER", "IMMIGRATION_DETAINEE", "REMAND", "UNKNOWN", "OTHER"],
  )
  @DiffableProperty(DiffCategory.STATUS)
  var legalStatus: String? = null

  @Schema(description = "The prisoner's imprisonment status code.", example = "LIFE")
  @DiffableProperty(DiffCategory.STATUS)
  var imprisonmentStatus: String? = null

  @Schema(description = "The prisoner's imprisonment status description.", example = "Serving Life Imprisonment")
  @DiffableProperty(DiffCategory.STATUS)
  var imprisonmentStatusDescription: String? = null

  @Schema(description = "The prisoner's convicted status code.", example = "Convicted", allowableValues = ["Convicted", "Remand"])
  @DiffableProperty(DiffCategory.STATUS)
  var convictedStatus: String? = null

  @Schema(required = true, description = "Most serious offence for this sentence", example = "Robbery")
  @DiffableProperty(DiffCategory.SENTENCE)
  var mostSeriousOffence: String? = null

  @Schema(description = "Indicates that the prisoner has been recalled", example = "false")
  @DiffableProperty(DiffCategory.STATUS)
  var recall: Boolean? = null

  @Schema(description = "Indicates that the prisoner has an indeterminate sentence", example = "true")
  @DiffableProperty(DiffCategory.SENTENCE)
  var indeterminateSentence: Boolean? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Start Date for this sentence", example = "2020-04-03")
  @DiffableProperty(DiffCategory.SENTENCE)
  var sentenceStartDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Actual of most likely Release Date", example = "2023-05-02")
  @DiffableProperty(DiffCategory.SENTENCE)
  var releaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Release Date Confirmed", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var confirmedReleaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Sentence Expiry Date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var sentenceExpiryDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Licence Expiry Date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var licenceExpiryDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "HDC Eligibility Date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var homeDetentionCurfewEligibilityDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "HDC Actual Date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var homeDetentionCurfewActualDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "HDC End Date", example = "2023-05-02")
  @DiffableProperty(DiffCategory.SENTENCE)
  var homeDetentionCurfewEndDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Top-up supervision start date", example = "2023-04-29")
  @DiffableProperty(DiffCategory.SENTENCE)
  var topupSupervisionStartDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Top-up supervision expiry date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var topupSupervisionExpiryDate: LocalDate? = null

  @Schema(description = "Days added to sentence term due to adjustments.", example = "10")
  @DiffableProperty(DiffCategory.SENTENCE)
  var additionalDaysAwarded: Int? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(
    description = "Release date for Non determinant sentence (if applicable). This will be based on one of ARD, CRD, NPD or PRRD.",
    example = "2023-05-01",
  )
  @DiffableProperty(DiffCategory.SENTENCE)
  var nonDtoReleaseDate: LocalDate? = null

  @Schema(
    description = "Indicates which type of non-DTO release date is the effective release date. One of 'ARD’, 'CRD’, ‘NPD’ or 'PRRD’.",
    example = "ARD",
    allowableValues = ["ARD", "CRD", "NPD", "PRRD"],
  )
  @DiffableProperty(DiffCategory.SENTENCE)
  var nonDtoReleaseDateType: String? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Date prisoner was received into prison at the start of the term", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var receptionDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Date prisoner was transferred into the current or latest prison", example = "2023-05-01")
  @DiffableProperty(DiffCategory.LOCATION)
  var lastAdmissionDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Parole  Eligibility Date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var paroleEligibilityDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Automatic Release Date. If automaticReleaseOverrideDate is available then it will be set as automaticReleaseDate", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var automaticReleaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Post Recall Release Date. if postRecallReleaseOverrideDate is available then it will be set as postRecallReleaseDate", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var postRecallReleaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Conditional Release Date. If conditionalReleaseOverrideDate is available then it will be set as conditionalReleaseDate", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var conditionalReleaseDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Actual Parole Date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var actualParoleDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Tariff Date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var tariffDate: LocalDate? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Release on Temporary Licence Date", example = "2023-05-01")
  @DiffableProperty(DiffCategory.SENTENCE)
  var releaseOnTemporaryLicenceDate: LocalDate? = null

  @Schema(
    description = "current prison or outside with last movement information.",
    example = "Outside - released from Leeds",
  )
  @DiffableProperty(DiffCategory.LOCATION)
  var locationDescription: String? = null

  @Schema(required = true, description = "Indicates a restricted patient. Will never be null.", example = "true")
  @DiffableProperty(DiffCategory.RESTRICTED_PATIENT)
  var restrictedPatient: Boolean? = null

  @Schema(description = "Supporting prison ID for POM", example = "LEI")
  @DiffableProperty(DiffCategory.RESTRICTED_PATIENT)
  var supportingPrisonId: String? = null

  @Schema(description = "Which hospital the prisoner has been discharged to", example = "HAZLWD")
  @DiffableProperty(DiffCategory.RESTRICTED_PATIENT)
  var dischargedHospitalId: String? = null

  @Schema(description = "Hospital name to which the prisoner was discharged", example = "Hazelwood House")
  @DiffableProperty(DiffCategory.RESTRICTED_PATIENT)
  var dischargedHospitalDescription: String? = null

  @Field(type = FieldType.Date, format = [DateFormat.date])
  @Schema(description = "Date of discharge", example = "2020-05-01")
  @DiffableProperty(DiffCategory.RESTRICTED_PATIENT)
  var dischargeDate: LocalDate? = null

  @Schema(description = "Any additional discharge details", example = "Psychiatric Hospital Discharge to Hazelwood House")
  var dischargeDetails: String? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "Incentive level")
  @DiffableProperty(DiffCategory.INCENTIVE_LEVEL)
  var currentIncentive: CurrentIncentive? = null

  @Schema(description = "Height in centimetres of the prisoner", example = "200")
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var heightCentimetres: Int? = null

  @Schema(description = "Weight in kilograms of the prisoner", example = "102")
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var weightKilograms: Int? = null

  @Schema(
    description = "Hair colour. From PROFILE_CODES table where PROFILE_TYPE = HAIR. Allowable values extracted 07/02/2023.",
    example = "Blonde",
    allowableValues = ["Bald", "Balding", "Black", "Blonde", "Brown", "Brunette", "Dark", "Dyed", "Ginger", "Grey", "Light", "Mouse", "Multi-coloured", "Red", "White"],
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var hairColour: String? = null

  @Schema(
    description = "Right eye colour. From PROFILE_CODES table where PROFILE_TYPE = R_EYE_C. Allowable values extracted 07/02/2023.",
    example = "Green",
    allowableValues = ["Blue", "Brown", "Clouded", "Green", "Grey", "Hazel", "Missing", "Pink", "White"],
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var rightEyeColour: String? = null

  @Schema(
    description = "Left eye colour. From PROFILE_CODES table where PROFILE_TYPE = L_EYE_C. Allowable values extracted 07/02/2023.",
    example = "Hazel",
    allowableValues = ["Blue", "Brown", "Clouded", "Green", "Grey", "Hazel", "Missing", "Pink", "White"],
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var leftEyeColour: String? = null

  @Schema(
    description = "Facial hair. From PROFILE_CODES table where PROFILE_TYPE = FACIAL_HAIR. Allowable values extracted 07/02/2023.",
    example = "Clean Shaven",
    allowableValues = ["Full Beard", "Clean Shaven", "Goatee Beard", "Moustache Only", "Not Applicable (Female Offender)", "No Facial Hair", "Sideburns"],
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var facialHair: String? = null

  @Schema(
    description = "Shape of face. From PROFILE_CODES table where PROFILE_TYPE = FACE. Allowable values extracted 07/02/2023.",
    example = "Round",
    allowableValues = ["Angular", "Bullet", "Oval", "Round", "Square", "Triangular"],
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var shapeOfFace: String? = null

  @Schema(
    description = "Build. From PROFILE_CODES table where PROFILE_TYPE = BUILD. Allowable values extracted 07/02/2023.",
    example = "Muscular",
    allowableValues = ["Fat", "Frail", "Heavy", "Medium", "Muscular", "Obese", "Proportional", "Slight", "Small", "Stocky", "Stooped", "Thin"],
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var build: String? = null

  @Schema(
    description = "UK shoe size",
    example = "10",
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var shoeSize: Int? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(
    description = "List of parts of the body that have tattoos. This includes marks and other marks whose comment contains 'tattoo'. 'From REFERENCE_CODES table where DOMAIN = BODY_PART. Allowable values extracted 08/02/2023.",
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var tattoos: List<BodyPartDetail>? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(
    description = "List of parts of the body that have scars. This includes marks and other marks whose comment contains 'scar'. From REFERENCE_CODES table where DOMAIN = BODY_PART. Allowable values extracted 08/02/2023.",
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var scars: List<BodyPartDetail>? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(
    description = "List of parts of the body that have marks. This includes NOMIS physical details of type 'marks' and 'otherMarks'. If we find a comment with either 'tattoo' or 'scar' we also add to the list of tattoos or scars. From REFERENCE_CODES table where DOMAIN = BODY_PART. Allowable values extracted 08/02/2023.",
  )
  @DiffableProperty(DiffCategory.PHYSICAL_DETAILS)
  var marks: List<BodyPartDetail>? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "Addresses. Note that no fixed addresses are only ever returned if they are also the primary address, otherwise they are filtered out.")
  @DiffableProperty(DiffCategory.CONTACT_DETAILS)
  var addresses: List<Address>? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "Email addresses")
  @DiffableProperty(DiffCategory.CONTACT_DETAILS)
  var emailAddresses: List<EmailAddress>? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "Telephone numbers. Note that the number will contain only numeric characters [0-9] (including no break between area code and number). Therefore if searching on 'phoneNumbers.number' you should not pass any non-numeric characters.")
  @DiffableProperty(DiffCategory.CONTACT_DETAILS)
  var phoneNumbers: List<PhoneNumber>? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "All identifiers for the prisoner including those recorded against aliases. Currently supports only PNC, CRO, NINO and DL.")
  @DiffableProperty(DiffCategory.IDENTIFIERS)
  var identifiers: List<Identifier>? = null

  @Field(type = FieldType.Nested, includeInParent = true)
  @Schema(description = "All historical convicted offences")
  @DiffableProperty(DiffCategory.SENTENCE)
  var allConvictedOffences: List<Offence>? = null

  override fun diff(other: Prisoner): DiffResult<Prisoner> = getDiffResult(this, other)

  @get:JsonIgnore
  val active: Boolean
    get() = status?.startsWith("ACTIVE") ?: false
}

data class BodyPartDetail(
  @Schema(
    description = "Part of the body that has the mark. From REFERENCE_CODES table where DOMAIN = BODY_PART. Allowable values extracted 08/02/2023.",
    example = "Head",
    allowableValues = ["Ankle", "Arm", "Ear", "Elbow", "Face", "Finger", "Foot", "Hand", "Head", "Knee", "Leg", "Lip", "Neck", "Nose", "Shoulder", "Thigh", "Toe", "Torso"],
  )
  var bodyPart: String? = null,
  @Schema(
    description = "Optional free text comment describing the mark",
    example = "Skull and crossbones covering chest",
  ) var comment: String? = null,
)
