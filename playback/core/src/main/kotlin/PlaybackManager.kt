// Modified for the Parmesh fork on 2026-10-13. See FORK.md.
package org.jellyfin.playback.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.playback.core.backend.BackendService
import org.jellyfin.playback.core.backend.PlayerBackend
import org.jellyfin.playback.core.plugin.PlayerService
import kotlin.reflect.KClass

class PlaybackManager internal constructor(
	val backend: PlayerBackend,
	private val services: MutableList<PlayerService>,
	val options: PlaybackManagerOptions,
	parentJob: Job? = null,
) {
	internal val backendService = BackendService().also { service ->
		service.switchBackend(backend)
	}

	private val job = SupervisorJob(parentJob)
	val state: PlayerState = MutablePlayerState(
		options = options,
		backendService = backendService,
		queue = getService()
	)

	init {
		services.forEach { it.initialize(this, state, Job(job)) }
	}


	fun <T : PlayerService> getService(kclass: KClass<T>): T? {
		for (service in services) {
			@Suppress("UNCHECKED_CAST")
			if (kclass.isInstance(service)) return service as T
		}
		return null
	}

	inline fun <reified T : PlayerService> getService() = getService(T::class)

}
