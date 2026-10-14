// Modified for the Parmesh fork on 2026-10-14. See FORK.md.
package org.jellyfin.androidtv.ui.browsing

import io.kotest.assertions.throwables.shouldThrow
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
	test("shorts queries are scoped to the selected movie library and use lightweight paging fields") {
		val requests = AutomaticFolders.shortsRequests(library(CollectionType.MOVIES))
		requests.size shouldBe 2
		requests.forEach { query ->
			query.parentId shouldBe libraryId
			query.includeItemTypes shouldBe setOf(BaseItemKind.MOVIE)
			query.recursive shouldBe true
			query.sortBy shouldBe setOf(org.jellyfin.sdk.model.api.ItemSortBy.SORT_NAME)
			query.limit shouldBe null
			query.fields?.contains(ItemFields.MEDIA_SOURCES) shouldBe false
			query.fields?.contains(ItemFields.DISPLAY_PREFERENCES_ID) shouldBe true
		}
	}

	test("short genres and tags are alternatives and do not inherit Family filters") {
		val (genres, tags) = AutomaticFolders.shortsRequests(library(CollectionType.MOVIES))
		genres.genres shouldBe setOf("Short", "Shorts", "Short Film", "Short Films")
		genres.tags shouldBe null
		genres.officialRatings shouldBe null
		tags.tags shouldBe AutomaticFolders.shortNames
		tags.genres shouldBe null
		tags.officialRatings shouldBe null
	}

	test("Shorts is available only for movie libraries") {
		AutomaticFolders.supportsShorts(library(CollectionType.MOVIES)) shouldBe true
		for (type in CollectionType.entries.filterNot { it == CollectionType.MOVIES }) {
			AutomaticFolders.supportsShorts(library(type)) shouldBe false
			shouldThrow<IllegalArgumentException> { AutomaticFolders.shortsRequests(library(type)) }
		}
	}

	test("movie genre folders collapse Family and Shorts aliases into their automatic folders") {
		val names = listOf("Short", "SHORTS", " short film ", "Short Films", "Kids", "family", "Comedy", "Action")
		val genres = names.map { BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.GENRE, name = it) }
		AutomaticFolders.genreFolders(library(CollectionType.MOVIES), genres) shouldBe listOf("Action", "Comedy")
		AutomaticFolders.genreFolders(library(CollectionType.TVSHOWS), genres) shouldBe listOf("Action", "Comedy", "Short", "short film", "Short Films", "SHORTS")
	}

})
