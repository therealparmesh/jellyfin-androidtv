// Modified for the Parmesh fork on 2026-10-15. See FORK.md.
package org.jellyfin.androidtv.ui.browsing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import java.util.UUID

class BrowsingUtilsTests : FunSpec({
	test("movie and show grids query their library even without child counts and use lightweight fields") {
		for ((collection, kind) in listOf(CollectionType.MOVIES to BaseItemKind.MOVIE, CollectionType.TVSHOWS to BaseItemKind.SERIES)) {
			val library = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.COLLECTION_FOLDER, collectionType = collection)
			val query = BrowsingUtils.createBrowseGridItemsRequest(library)
			query.parentId shouldBe library.id
			query.includeItemTypes shouldBe setOf(kind)
			query.recursive shouldBe true
			query.fields?.contains(ItemFields.MEDIA_SOURCES) shouldBe false
			query.fields?.contains(ItemFields.DISPLAY_PREFERENCES_ID) shouldBe true
		}
	}
})
