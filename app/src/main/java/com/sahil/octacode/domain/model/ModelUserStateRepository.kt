package com.sahil.octacode.domain.model

import com.sahil.octacode.core.model.ModelUserState
import kotlinx.coroutines.flow.StateFlow

/**
 * What *this user* has done with each model — which ones they starred, which
 * ones they last reached for — kept apart from the immutable catalog.
 *
 * An interface rather than the store itself because [com.sahil.octacode.domain.chat.ChatEngine]
 * needs to record a model as used, and domain does not depend on data here any
 * more than it does on Room: [com.sahil.octacode.data.model.ModelUserStateStore]
 * implements this the way RoomChatRepository implements ChatRepository.
 */
interface ModelUserStateRepository {

    /**
     * Reactive, because the selector renders straight off it.
     *
     * A `StateFlow` rather than a `Flow`: what the user has starred already
     * exists before the first frame, so the picker must not open blank and
     * then populate — and a caller of a plain flow would have to invent an
     * initial value that is really just a guess.
     */
    val states: StateFlow<Map<String, ModelUserState>>

    fun setFavorite(modelId: String, favorite: Boolean)

    /**
     * Records that [modelId] was actually used.
     *
     * Deliberately not called when a model is *tapped* in the picker: opening
     * a list to see what is in it would fill Recent with models the user never
     * sent a token through.
     */
    fun markUsed(modelId: String, at: Long)
}
