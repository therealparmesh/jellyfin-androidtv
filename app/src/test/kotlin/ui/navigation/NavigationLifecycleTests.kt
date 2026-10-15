package org.jellyfin.androidtv.ui.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationLifecycleTests : FunSpec({
	test("navigation issued while a host is stopped resumes in order with its full history") {
		runTest {
			Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
			try {
				val registry = LifecycleRegistry.createUnsafe(mockk<LifecycleOwner>())
				registry.currentState = Lifecycle.State.STARTED
				fun destination() = Destination.Fragment(Fragment::class, mockk<Bundle>())
				val repository = NavigationRepositoryImpl(destination())
				val actions = mutableListOf<NavigationAction>()
				backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
					repository.actionsWhileStarted(registry).collect { actions.add(it) }
				}
				repository.reset(clearHistory = true)
				runCurrent()
				registry.currentState = Lifecycle.State.CREATED
				runCurrent()
				repository.navigate(destination())
				repository.navigate(destination())
				repository.goBack()
				runCurrent()
				actions.size shouldBe 1
				registry.currentState = Lifecycle.State.STARTED
				runCurrent()
				actions.map { it.id } shouldBe listOf(1L, 2L, 3L, 4L)
				registry.currentState = Lifecycle.State.DESTROYED
			} finally { Dispatchers.resetMain() }
		}
	}

	test("stopping and resuming a host cannot apply its last navigation twice") {
		runTest {
			Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
			try {
				val registry = LifecycleRegistry.createUnsafe(mockk<LifecycleOwner>())
				registry.currentState = Lifecycle.State.CREATED
				fun destination() = Destination.Fragment(Fragment::class, mockk<Bundle>())
				val repository = NavigationRepositoryImpl(destination())
				val actions = mutableListOf<NavigationAction>()
				backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
					repository.actionsWhileStarted(registry).collect { actions.add(it) }
				}
				repository.reset(clearHistory = true)
				runCurrent()
				actions.size shouldBe 0
				registry.currentState = Lifecycle.State.STARTED
				runCurrent()
				actions.size shouldBe 1
				registry.currentState = Lifecycle.State.CREATED
				runCurrent()
				registry.currentState = Lifecycle.State.STARTED
				runCurrent()
				actions.size shouldBe 1
				repository.navigate(destination())
				runCurrent()
				repository.goBack()
				runCurrent()
				repository.navigate(destination())
				runCurrent()
				repository.goBack()
				runCurrent()
				actions.size shouldBe 5
				actions.map { it.id }.distinct().size shouldBe 5
				registry.currentState = Lifecycle.State.DESTROYED
				runCurrent()
			} finally {
				Dispatchers.resetMain()
			}
		}
	}
})
