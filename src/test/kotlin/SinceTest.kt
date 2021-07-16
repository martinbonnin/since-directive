import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.network.http.DefaultHttpEngine
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import com.apollographql.apollo3.network.http.HttpNetworkTransport
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// https://github.com/martinbonnin/since-directive/discussions/1
const val TEST_DISCUSSION_ID = "MDEwOkRpc2N1c3Npb24zNDYzNTg2"

// https://github.com/martinbonnin/since-directive/issues/2
const val TEST_ISSUE_ID = "MDU6SXNzdWU5NDYyNjgwMjI="

const val SUCCESSFUL_DISCUSSION_RESPONSE = """
  {
  "data": {
    "lockLockable": {
      "actor": {
        "login": "martinbonnin"
      },
      "lockedRecord": {
        "activeLockReason": "RESOLVED",
        "__typename": "Discussion",
        "locked": true,
        "id": "MDEwOkRpc2N1c3Npb24zNDYzNTg2"
      }
    }
  }
}
"""

const val SUCCESSFUL_ISSUE_RESPONSE = """
  {
  "data": {
    "lockLockable": {
      "actor": {
        "login": "martinbonnin"
      },
      "lockedRecord": {
        "activeLockReason": null,
        "__typename": "Issue",
        "locked": true,
        "id": "MDU6SXNzdWU5NDYyNjgwMjI="
      }
    }
  }
}
"""

class SinceTest {
  /**
   * V4 has Discussion, so we can lock it \o/
   */
  @Test
  fun lockDiscussionV4() = runBlocking {
    val mockServer = MockWebServer()
    mockServer.enqueue(MockResponse().setBody(SUCCESSFUL_DISCUSSION_RESPONSE))

    val apolloClient = apolloClient(mockServer.url("/").toString(), 4)

    val response = apolloClient.mutate(LockLockableMutation(TEST_DISCUSSION_ID))

    val discussionId = response.data
      ?.lockLockable
      ?.lockedRecord
      ?.lockableFragment
      ?.onDiscussion
      ?.id

    assertEquals(null, response.errors)
    assertEquals(TEST_DISCUSSION_ID, discussionId)
  }

  /**
   * V3 doesn't have Discussion but still has Issue, so we should be able to lock an Issue using the same models
   */
  @Test
  fun lockIssueV3() = runBlocking {
    val mockServer = MockWebServer()
    mockServer.enqueue(MockResponse().setBody(SUCCESSFUL_ISSUE_RESPONSE))

    val apolloClient = apolloClient(mockServer.url("/").toString(), 3)

    val response = apolloClient.mutate(LockLockableMutation(TEST_ISSUE_ID))

    val discussionId = response.data
      ?.lockLockable
      ?.lockedRecord
      ?.lockableFragment
      ?.onIssue
      ?.id

    assertEquals(null, response.errors)
    assertEquals(TEST_ISSUE_ID, discussionId)

    // And make sure the request body doesn't mention "Discussion"
    val recordedRequest = mockServer.takeRequest()
    assertFalse(recordedRequest.body.readUtf8().contains("Discussion"))
  }

  private fun apolloClient(serverUrl: String, serverVersion: Int): ApolloClient {
    return ApolloClient(
      HttpNetworkTransport(
        httpRequestComposer = VersionAwareRequestComposer(
          endpoint = serverUrl, // "https://api.github.com/graphql",
          serverVersion = serverVersion
        ),
        interceptors = listOf(object : HttpInterceptor {
          override suspend fun intercept(request: HttpRequest, chain: HttpInterceptorChain): HttpResponse {
            val token = try {
              File("github_token").readText().trim()
            } catch (e: Exception) {
              // Since these tests use MockServer, there's no real need to send a token
              "unused"
            }
            val newRequest = request.copy(
              headers = request.headers + ("Authorization" to "bearer $token")
            )
            return chain.proceed(newRequest)
          }

        }),
        engine = DefaultHttpEngine()
      )
    )
  }

  private suspend fun ensureNotLocked(apolloClient: ApolloClient) {
    kotlin.runCatching {
      apolloClient.mutate(UnlockLockableMutation(id = TEST_DISCUSSION_ID))
    }
  }
}