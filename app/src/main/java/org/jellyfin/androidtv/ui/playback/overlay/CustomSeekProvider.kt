// Modified for the Parmesh fork on 2026-10-12. See FORK.md.
package org.jellyfin.androidtv.ui.playback.overlay

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.leanback.widget.PlaybackSeekDataProvider
import coil3.ImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.transformations
import coil3.size.Dimension
import coil3.size.Size
import coil3.toBitmap
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.util.coil.SubsetTransformation
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.TrickplayInfoDto
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

class CustomSeekProvider(
	private val videoPlayerAdapter: VideoPlayerAdapter,
	private val imageLoader: ImageLoader,
	private val api: ApiClient,
	private val context: Context,
	private val forwardTime: Long
) : PlaybackSeekDataProvider() {
	private companion object {
		const val MAX_SEEK_POINTS = 10_000L
		const val DEFAULT_FORWARD_TIME = 10_000L
	}

	private val imageRequests = mutableMapOf<Int, Disposable>()
	private var currentSeekPositions = LongArray(0)

	private var cachedPlaceholderThumbnail: Bitmap? = null

	override fun getSeekPositions(): LongArray {
		if (!videoPlayerAdapter.canSeek()) {
			currentSeekPositions = LongArray(0)
			return currentSeekPositions
		}

		val videoEndPosition = videoPlayerAdapter.duration.coerceAtLeast(0)
		val currentSeekPosition = videoPlayerAdapter.currentPosition.coerceIn(0, videoEndPosition)
		// Bound allocations for bogus durations or unusually small intervals while retaining the current position.
		val minimumStep = videoEndPosition / MAX_SEEK_POINTS + if (videoEndPosition % MAX_SEEK_POINTS == 0L) 0 else 1
		val step = (forwardTime.takeIf { it > 0 } ?: DEFAULT_FORWARD_TIME).coerceAtLeast(minimumStep).coerceAtLeast(1)
		val seekPositions = ArrayList<Long>((videoEndPosition / step).toInt() + 3)
		seekPositions.add(0L)
		var position = currentSeekPosition % step
		while (position < videoEndPosition) {
			if (position > 0) seekPositions.add(position)
			if (videoEndPosition - position <= step) break
			position += step
		}
		if (videoEndPosition > 0) seekPositions.add(videoEndPosition)

		currentSeekPositions = seekPositions.toLongArray()
		return currentSeekPositions
	}

	override fun getThumbnail(index: Int, callback: ResultCallback) {
		if (index !in currentSeekPositions.indices) return

		val currentRequest = imageRequests[index]
		if (currentRequest?.isDisposed == false) currentRequest.dispose()

		val item = videoPlayerAdapter.currentlyPlayingItem
		val mediaSource = videoPlayerAdapter.currentMediaSource
		val mediaSourceId = mediaSource?.id?.toUUIDOrNull()
		if (item == null || mediaSource == null || mediaSourceId == null) return

		val trickPlayResolutions = item.trickplay?.get(mediaSource.id)
		val trickPlayInfo = trickPlayResolutions?.values?.firstOrNull() ?: return

		val tile = trickPlayInfo.tileAt(currentSeekPositions[index]) ?: return

		val url = api.trickplayApi.getTrickplayTileImageUrl(
			itemId = item.id,
			width = trickPlayInfo.width,
			index = tile.index,
			mediaSourceId = mediaSourceId,
		)

		val placeholderThumbnail = getPlaceholderThumbnail(trickPlayInfo.width, trickPlayInfo.height)

		imageRequests[index] = imageLoader.enqueue(ImageRequest.Builder(context).apply {
			data(url)
			size(Size.ORIGINAL)
			maxBitmapSize(Size(Dimension.Undefined, Dimension.Undefined))
			httpHeaders(NetworkHeaders.Builder().apply {
				set(
					key = "Authorization",
					value = AuthorizationHeaderBuilder.buildHeader(
						api.clientInfo.name,
						api.clientInfo.version,
						api.deviceInfo.id,
						api.deviceInfo.name,
						api.accessToken
					)
				)
			}.build())

			transformations(SubsetTransformation(tile.offsetX, tile.offsetY, trickPlayInfo.width, trickPlayInfo.height))

			target(
				onStart = { _ -> callback.onThumbnailLoaded(placeholderThumbnail, index) },
				onError = { _ -> callback.onThumbnailLoaded(placeholderThumbnail, index) },
				onSuccess = { image ->
					val bitmap = image.toBitmap()
					callback.onThumbnailLoaded(bitmap, index)
				}
			)
		}.build())
	}

	override fun reset() {
		for (request in imageRequests.values) {
			if (!request.isDisposed) request.dispose()
		}
		imageRequests.clear()

		currentSeekPositions = LongArray(0)
	}

	fun getPlaceholderThumbnail(width: Int, height: Int): Bitmap {
		if (cachedPlaceholderThumbnail?.width == width && cachedPlaceholderThumbnail?.height == height) {
			return cachedPlaceholderThumbnail!!
		}

		val color = ContextCompat.getColor(context, R.color.black_transparent_light)
		val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		result.eraseColor(color)
		cachedPlaceholderThumbnail = result
		return result
	}
}

internal data class TrickplayTile(val index: Int, val offsetX: Int, val offsetY: Int)

internal fun TrickplayInfoDto.tileAt(timeMs: Long): TrickplayTile? {
	if (timeMs < 0 || interval <= 0 || width <= 0 || height <= 0 || tileWidth <= 0 || tileHeight <= 0 || thumbnailCount <= 0) return null
	val tilesPerSheet = tileWidth.toLong() * tileHeight
	if (tilesPerSheet > Int.MAX_VALUE || width.toLong() * tileWidth > Int.MAX_VALUE || height.toLong() * tileHeight > Int.MAX_VALUE) return null
	// The end seek point is the duration and may fall immediately after the last thumbnail.
	val frame = timeMs.floorDiv(interval).coerceAtMost((thumbnailCount - 1).toLong())
	val offset = frame % tilesPerSheet
	return TrickplayTile(
		index = (frame / tilesPerSheet).toInt(),
		offsetX = ((offset % tileWidth) * width).toInt(),
		offsetY = ((offset / tileWidth) * height).toInt(),
	)
}
