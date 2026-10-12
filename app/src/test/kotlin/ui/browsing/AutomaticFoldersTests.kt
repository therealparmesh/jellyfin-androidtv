package org.jellyfin.androidtv.ui.browsing

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import java.util.UUID

class AutomaticFoldersTests : FunSpec({
	val libraryId = UUID.randomUUID()
	fun library(type: CollectionType) = BaseItemDto(id = libraryId, type = BaseItemKind.COLLECTION_FOLDER, collectionType = type)

	test("movie and show family requests remain scoped to the library with the correct item type") {
		for ((collection, kind) in listOf(CollectionType.MOVIES to BaseItemKind.MOVIE, CollectionType.TVSHOWS to BaseItemKind.SERIES)) {
			val requests = AutomaticFolders.familyRequests(library(collection))
			requests.size shouldBe 3
			requests.forEach { query ->
				query.parentId shouldBe libraryId
				query.includeItemTypes shouldBe setOf(kind)
				query.recursive shouldBe true
				query.fields?.contains(ItemFields.MEDIA_SOURCES) shouldBe false
				query.fields?.contains(ItemFields.CHAPTERS) shouldBe false
			}
		}
	}

	test("family criteria are alternatives rather than intersected filters") {
		val (genres, tags, ratings) = AutomaticFolders.familyRequests(library(CollectionType.MOVIES))
		genres.genres shouldBe AutomaticFolders.familyNames
		genres.tags shouldBe null
		genres.officialRatings shouldBe null
		tags.tags shouldBe AutomaticFolders.familyNames
		tags.genres shouldBe null
		ratings.officialRatings shouldBe AutomaticFolders.familyRatings
		ratings.genres shouldBe null
		ratings.tags shouldBe null
		AutomaticFolders.familyNames.contains("Animation") shouldBe false
		AutomaticFolders.familyRatings.contains("R") shouldBe false
		AutomaticFolders.familyRatings.contains("PG-13") shouldBe false
	}

	test("genres are sorted and blank and repeated names are removed") {
		val genres = listOf("Comedy", "", "  ", "Action", "comedy", null).map { BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.GENRE, name = it) }
		AutomaticFolders.genreNames(genres) shouldBe listOf("Action", "Comedy")
	}

	test("genre query works without childCount and preserves type and parent") {
		val query = BrowsingUtils.createBrowseGridItemsRequest(library(CollectionType.TVSHOWS), "Comedy")
		query.parentId shouldBe libraryId
		query.includeItemTypes shouldBe setOf(BaseItemKind.SERIES)
		query.genres shouldBe setOf("Comedy")
		query.recursive shouldBe true
		query.fields?.contains(ItemFields.DISPLAY_PREFERENCES_ID) shouldBe true
	}
})
