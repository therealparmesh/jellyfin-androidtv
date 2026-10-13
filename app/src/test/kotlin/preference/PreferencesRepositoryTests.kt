package org.jellyfin.androidtv.preference

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.operations.DisplayPreferencesApi
import org.jellyfin.sdk.model.api.DisplayPreferencesDto

class PreferencesRepositoryTests : FunSpec({
	fun fixture(): Pair<PreferencesRepository, DisplayPreferencesApi> {
		val endpoint = mockk<DisplayPreferencesApi>()
		val api = mockk<ApiClient> {
			every { getOrCreateApi(DisplayPreferencesApi::class, any()) } returns endpoint
		}
		return PreferencesRepository(api, mockk(relaxed = true), mockk(relaxed = true)) to endpoint
	}
	fun response() = mockk<Response<DisplayPreferencesDto>> {
		every { content } returns mockk { every { customPrefs } returns mapOf("SmartScreen" to "true") }
	}

	test("cache access never performs a server request") {
		val (repository, endpoint) = fixture()
		repository.getLibraryPreferences("library").shouldUpdate shouldBe true
		coVerify(exactly = 0) { endpoint.getDisplayPreferences(any(), any(), any()) }
	}
	test("concurrent loads suspend without blocking and fetch saved values once") {
		val (repository, endpoint) = fixture()
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		coEvery { endpoint.getDisplayPreferences(any(), any(), any()) } coAnswers {
			started.complete(Unit)
			release.await()
			response()
		}
		coroutineScope {
			val first = async { repository.loadLibraryPreferences("library") }
			started.await()
			val second = async { repository.loadLibraryPreferences("library") }
			first.isCompleted shouldBe false
			release.complete(Unit)
			first.await()[LibraryPreferences.enableSmartScreen] shouldBe true
			second.await() shouldBe first.await()
		}
		coVerify(exactly = 1) { endpoint.getDisplayPreferences(any(), any(), any()) }
	}
	test("session changes invalidate loaded library preferences") {
		val (repository, endpoint) = fixture()
		coEvery { endpoint.getDisplayPreferences(any(), any(), any()) } returns response()
		val before = repository.loadLibraryPreferences("library")
		repository.onSessionChanged()
		(repository.loadLibraryPreferences("library") === before) shouldBe false
		coVerify(exactly = 2) { endpoint.getDisplayPreferences(any(), any(), any()) }
	}
})
