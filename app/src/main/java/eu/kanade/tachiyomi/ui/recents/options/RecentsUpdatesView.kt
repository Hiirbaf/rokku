package eu.kanade.tachiyomi.ui.recents.options

import android.content.Context
import android.util.AttributeSet
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.databinding.RecentsUpdatesViewBinding
import eu.kanade.tachiyomi.util.bindToPreference
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.BaseRecentsDisplayView
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yokai.domain.category.interactor.GetCategories
import yokai.i18n.MR
import yokai.util.lang.getString
import android.R as AR

class RecentsUpdatesView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    BaseRecentsDisplayView<RecentsUpdatesViewBinding>(context, attrs) {

    private val getCategories: GetCategories by lazy { Injekt.get() }

    override fun inflateBinding() = RecentsUpdatesViewBinding.bind(this)
    override fun initGeneralPreferences() {
        binding.showUpdatedTime.bindToPreference(preferences.showUpdatedTime())
        binding.sortFetchedTime.bindToPreference(preferences.sortFetchedTime())
        binding.groupChapters.bindToPreference(preferences.collapseGroupedUpdates()) {
            controller?.presenter?.expandedSectionsMap?.clear()
        }
        updateFilterCategoriesButton()
        binding.filterCategories.setOnClickListener { showFilterCategoriesDialog() }
    }

    private fun updateFilterCategoriesButton() {
        val selectedCount = recentsPreferences.filterUpdatesCategories().get().size
        binding.filterCategories.text = if (selectedCount > 0) {
            context.getString(MR.strings.categories) + " ($selectedCount)"
        } else {
            context.getString(MR.strings.categories)
        }
    }

    private fun showFilterCategoriesDialog() {
        val activity = controller?.activity ?: return
        controller?.viewScope?.launch {
            val categories = getCategories.await().filterNot { it.isSystem || it.isDynamic }
            if (categories.isEmpty()) {
                activity.toast(MR.strings.no_categories)
                return@launch
            }
            showFilterCategoriesDialog(activity, categories)
        }
    }

    private fun showFilterCategoriesDialog(activity: android.app.Activity, categories: List<Category>) {
        val selectedIds = recentsPreferences.filterUpdatesCategories().get()
        val checkedItems = categories.map { it.id.toString() in selectedIds }.toBooleanArray()
        activity.materialAlertDialog()
            .setTitle(activity.getString(MR.strings.categories))
            .setMultiChoiceItems(
                categories.map { it.name }.toTypedArray(),
                checkedItems,
            ) { _, position, checked -> checkedItems[position] = checked }
            .setPositiveButton(AR.string.ok) { _, _ ->
                val newSelection = categories.filterIndexed { index, _ -> checkedItems[index] }
                    .mapNotNull { it.id?.toString() }
                    .toSet()
                recentsPreferences.filterUpdatesCategories().set(newSelection)
                updateFilterCategoriesButton()
            }
            .setNegativeButton(AR.string.cancel, null)
            .show()
    }
}
