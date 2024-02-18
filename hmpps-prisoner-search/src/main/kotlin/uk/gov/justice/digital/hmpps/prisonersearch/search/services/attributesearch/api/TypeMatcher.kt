package uk.gov.justice.digital.hmpps.prisonersearch.search.services.attributesearch.api

import kotlin.reflect.KClass
import kotlin.reflect.KType

sealed interface TypeMatcher<S> {
  val attribute: String
  fun validate() {}
  fun genericType(): KClass<*> = this::class.genericType()
  companion object {
    @JvmStatic
    fun getSupportedTypes() = TypeMatcher::class.sealedSubclasses.map { it.genericType() }
  }
}

private fun KClass<*>.genericType() = this.supertypes.first().genericType()
internal fun KType.genericType() = this.arguments.first().type!!.classifier as KClass<*>
