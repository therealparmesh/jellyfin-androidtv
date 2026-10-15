package org.jellyfin.androidtv.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RouterTests : FunSpec({
	test("back at the settings root leaves a renderable route") {
		val root = RouteContext("/", emptyMap())
		val router = Router(emptyMap(), mutableStateListOf(root))
		router.back()
		router.backStack.toList() shouldBe listOf(root)
	}
	test("back removes a child while preserving the root") {
		val root = RouteContext("/", emptyMap())
		val router = Router(emptyMap(), mutableStateListOf(root, RouteContext("/child", emptyMap())))
		router.back()
		router.back()
		router.backStack.toList() shouldBe listOf(root)
	}
})
