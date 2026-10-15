package org.jellyfin.androidtv.ui.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class NavigationRepositoryTests : FunSpec({
	fun destination() = Destination.Fragment(Fragment::class, mockk<Bundle>())

	test("a reset retaining history can return to the preceding screen") {
		val repository = NavigationRepositoryImpl(destination())
		repository.reset(clearHistory = true)
		repository.navigate(destination())
		repository.reset(destination(), false)
		repository.goBack() shouldBe true
		repository.canGoBack shouldBe true
		repository.goBack() shouldBe true
		repository.canGoBack shouldBe false
	}
	test("clearing history establishes a new root") {
		val repository = NavigationRepositoryImpl(destination())
		repository.navigate(destination())
		repository.reset(clearHistory = true)
		repository.canGoBack shouldBe false
		repository.goBack() shouldBe false
	}
	test("replacing a directly opened root cannot invent a previous screen") {
		val repository = NavigationRepositoryImpl(destination())
		repository.reset(destination(), true)
		repository.navigate(destination(), replace = true)
		repository.canGoBack shouldBe false
		repository.goBack() shouldBe false
	}
	test("replacing a non-root screen preserves its previous screen") {
		val repository = NavigationRepositoryImpl(destination())
		repository.reset(clearHistory = true)
		repository.navigate(destination())
		repository.navigate(destination(), replace = true)
		repository.goBack() shouldBe true
		repository.canGoBack shouldBe false
	}
	test("consecutive back actions have distinct identities for replay suppression") {
		val repository = NavigationRepositoryImpl(destination())
		repository.navigate(destination())
		repository.navigate(destination())
		repository.goBack()
		val firstBack = repository.currentAction.replayCache.single()
		repository.goBack()
		val lastBack = repository.currentAction.replayCache.single()
		(firstBack is NavigationAction.GoBack) shouldBe true
		(lastBack is NavigationAction.GoBack) shouldBe true
		lastBack.id shouldBe firstBack.id + 1

	}
	test("rapid navigation cannot drop actions while an active consumer is busy") {
		val repository = NavigationRepositoryImpl(destination())
		val received = mutableListOf<NavigationAction>()
		val started = CompletableDeferred<Unit>()
		val release = CompletableDeferred<Unit>()
		val complete = CompletableDeferred<Unit>()
		coroutineScope {
			val job = launch(start = CoroutineStart.UNDISPATCHED) {
				repository.currentAction.collect {
					received.add(it)
					if (received.size == 1) { started.complete(Unit); release.await() }
					if (received.size == 101) complete.complete(Unit)
				}
			}
			repository.reset(clearHistory = true)
			started.await()
			repeat(100) { repository.navigate(destination()) }
			release.complete(Unit)
			withTimeout(2_000) { complete.await() }
			job.cancel()
		}
		received.map { it.id } shouldBe (1L..101L).toList()
	}
})
