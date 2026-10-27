package eu.kanade.tachiyomi.ui.extension.details

import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.base.presenter.BaseCoroutinePresenter
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.system.withUIContext
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.extension.repo.interactor.GetExtensionRepo
import yokai.domain.extension.repo.model.ExtensionRepo

class ExtensionDetailsPresenter(
    val pkgName: String,
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val getExtensionRepo: GetExtensionRepo = Injekt.get(),
) : BaseCoroutinePresenter<ExtensionDetailsController>() {

    val extension = extensionManager.installedExtensionsFlow.value.find { it.pkgName == pkgName }

    var repo: ExtensionRepo? = null
        private set

    override fun onCreate() {
        super.onCreate()
        bindToUninstalledExtension()
        loadRepo()
    }

    private fun loadRepo() {
        val repoUrl = extension?.repoUrl ?: return
        presenterScope.launch {
            val found = getExtensionRepo.getAll().find { it.baseUrl == repoUrl } ?: return@launch
            withUIContext {
                repo = found
                view?.onRepoLoaded()
            }
        }
    }

    private fun bindToUninstalledExtension() {
        extensionManager.installedExtensionsFlow
            .drop(1)
            .onEach { extensions ->
                extensions.filter { it.pkgName == pkgName }
                presenterScope.launchUI { view?.onExtensionUninstalled() }
            }
            .launchIn(presenterScope)
    }

    fun uninstallExtension() {
        val extension = extension ?: return
        extensionManager.uninstallExtension(extension.pkgName)
    }
}
