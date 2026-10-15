package org.jellyfin.androidtv.ui.presentation

import android.database.Observable
import androidx.leanback.widget.ObjectAdapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private class AdapterFixture {
	val adapter = MutableObjectAdapter<Int>()
	val observers = arrayListOf<ObjectAdapter.DataObserver>()
	init {
		// The host Android stub omits Observable's field initialization. Supply its observer list
		// so Leanback's real notification implementation can run without an Android runtime.
		val observable = ObjectAdapter::class.java.getDeclaredField("mObservable").apply { isAccessible = true }.get(adapter)
		Observable::class.java.getDeclaredField("mObservers").apply { isAccessible = true }.set(observable, observers)
	}
}

class MutableObjectAdapterTests : FunSpec({
	test("clear observers see the empty collection immediately") {
		val fixture = AdapterFixture()
		val adapter = fixture.adapter
		adapter.add(1); adapter.add(2)
		var observedSize = -1
		fixture.observers.add(object : ObjectAdapter.DataObserver() {
			override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { observedSize = adapter.size() }
		})
		adapter.clear()
		observedSize shouldBe 0
		adapter.size() shouldBe 0
	}
	test("invalid removal ranges are ignored without mutating the collection") {
		val fixture = AdapterFixture()
		val adapter = fixture.adapter
		adapter.add(1); adapter.add(2)
		for ((index, length) in listOf(-1 to 1, 2 to 1, 0 to -1, 0 to 0, 1 to 2, 1 to Int.MAX_VALUE)) {
			adapter.removeAt(index, length) shouldBe false
			adapter.toList() shouldBe listOf(1, 2)
		}
	}
	test("valid removal reports the range after updating the collection") {
		val fixture = AdapterFixture()
		val adapter = fixture.adapter
		adapter.add(1); adapter.add(2); adapter.add(3)
		var observed = emptyList<Int>()
		fixture.observers.add(object : ObjectAdapter.DataObserver() {
			override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
				positionStart shouldBe 1; itemCount shouldBe 2
				observed = adapter.toList()
			}
		})
		adapter.removeAt(1, 2) shouldBe true
		observed shouldBe listOf(1)
	}
})
