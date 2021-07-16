import com.apollographql.apollo3.api.*
import com.apollographql.apollo3.api.http.*
import com.apollographql.apollo3.ast.*

class VersionAwareRequestComposer(private val endpoint: String, private val serverVersion: Int) : HttpRequestComposer {

  override fun <D : Operation.Data> compose(apolloRequest: ApolloRequest<D>): HttpRequest {
    val operation = apolloRequest.operation

    val body = DefaultHttpRequestComposer.buildPostBody(
      operation,
      apolloRequest.executionContext[CustomScalarAdapters]!!,
      false,
      stripSinceDirectives(operation.document(), serverVersion)
    )

    return HttpRequest(
      method = HttpMethod.Post,
      url = endpoint,
      headers = emptyMap(),
      body = body
    )
  }

  private fun stripSinceDirectives(documentString: String, serverVersion: Int): String {
    val document = documentString.parseAsGQLDocument().getOrThrow()

    // First remove unsupported fields
    var transformed = document.transform {
      val directives = when(it) {
        is GQLField -> it.directives
        is GQLInlineFragment -> it.directives
        is GQLFragmentSpread -> it.directives
        else -> null
      }

      // if there's no directive, assume this is always available
      val nodeMinVersion = directives?.minVersion() ?: Int.MIN_VALUE
      if (nodeMinVersion > serverVersion) {
        null
      } else {
        it
      }
    }

    // Then remove the directives themselves as the server will not understand them
    transformed = transformed?.transform {
      if (it is GQLDirective && (it.name == "since")) {
        null
      } else {
        it
      }
    }

    return transformed!!.toUtf8()
  }

  private fun List<GQLDirective>.minVersion(): Int? {
    val directive = firstOrNull { it.name == "since" }

    if (directive == null) {
      return null
    }

    val argument = directive.arguments?.arguments?.first()

    check(argument != null && argument.name == "version") {
      "@since requires a single 'version' argument"
    }

    val value = argument.value
    check(value is GQLIntValue) {
      "@since requires an Int argument (is ${argument.value})"
    }

    return value.value
  }
}
