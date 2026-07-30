package yokai.presentation.libraryUpdateError

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.util.system.launchIO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.libraryUpdateError.LibraryUpdateErrorRepository
import yokai.domain.libraryUpdateError.model.LibraryUpdateErrorWithManga

class LibraryUpdateErrorScreenModel(
    private val libraryUpdateErrorRepository: LibraryUpdateErrorRepository = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<LibraryUpdateErrorScreenState>(LibraryUpdateErrorScreenState()) {

    private val selectedErrorIds: HashSet<Long> = HashSet()

    init {
        screenModelScope.launchIO {
            libraryUpdateErrorRepository.subscribeAll().collectLatest { errors ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        items = errors.toItems(),
                    )
                }
            }
        }
    }

    private fun List<LibraryUpdateErrorWithManga>.toItems(): List<LibraryUpdateErrorItem> {
        return map { error ->
            LibraryUpdateErrorItem(
                error = error,
                sourceName = sourceManager.getOrStub(error.mangaSource).name,
                selected = error.errorId in selectedErrorIds,
            )
        }
    }

    fun toggleSelection(item: LibraryUpdateErrorItem, selected: Boolean) {
        mutableState.update { state ->
            val index = state.items.indexOfFirst { it.error.errorId == item.error.errorId }
            if (index < 0) return@update state
            val newItems = state.items.toMutableList()
            newItems[index] = newItems[index].copy(selected = selected)
            if (selected) selectedErrorIds.add(item.error.errorId) else selectedErrorIds.remove(item.error.errorId)
            state.copy(items = newItems)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val newItems = state.items.map {
                if (selected) selectedErrorIds.add(it.error.errorId) else selectedErrorIds.remove(it.error.errorId)
                it.copy(selected = selected)
            }
            state.copy(items = newItems)
        }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val newItems = state.items.map {
                val newSelected = !it.selected
                if (newSelected) selectedErrorIds.add(it.error.errorId) else selectedErrorIds.remove(it.error.errorId)
                it.copy(selected = newSelected)
            }
            state.copy(items = newItems)
        }
    }

    fun deleteSelected() {
        val ids = selectedErrorIds.toList()
        if (ids.isEmpty()) return
        screenModelScope.launchIO {
            libraryUpdateErrorRepository.deleteByIds(ids)
            selectedErrorIds.clear()
        }
    }

    fun deleteAll() {
        screenModelScope.launchIO {
            libraryUpdateErrorRepository.deleteAll()
            selectedErrorIds.clear()
        }
    }

    fun getSelectedMangaIds(): List<Long> =
        state.value.items.filter { it.selected }.map { it.error.mangaId }
}

@Immutable
data class LibraryUpdateErrorItem(
    val error: LibraryUpdateErrorWithManga,
    val sourceName: String,
    val selected: Boolean = false,
)

@Immutable
data class LibraryUpdateErrorScreenState(
    val isLoading: Boolean = true,
    val items: List<LibraryUpdateErrorItem> = emptyList(),
) {
    val selected: List<LibraryUpdateErrorItem>
        get() = items.filter { it.selected }
}
