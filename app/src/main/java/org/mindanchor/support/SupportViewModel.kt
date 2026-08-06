package org.mindanchor.support

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mindanchor.data.db.AnchorDatabase
import org.mindanchor.data.db.CrisisContact
import org.mindanchor.data.db.SafetyPlan

class SupportViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AnchorDatabase.get(application).safety()

    val plan = dao.plan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val contacts = dao.contacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun savePlan(plan: SafetyPlan) {
        viewModelScope.launch {
            dao.savePlan(plan.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun addContact(name: String, phone: String, isProfessional: Boolean) {
        if (name.isBlank() && phone.isBlank()) return
        viewModelScope.launch {
            dao.addContact(
                CrisisContact(
                    name = name.trim(),
                    phone = phone.trim(),
                    isProfessional = isProfessional,
                ),
            )
        }
    }

    fun removeContact(contact: CrisisContact) {
        viewModelScope.launch { dao.removeContact(contact) }
    }
}
