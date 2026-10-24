package com.vehicare.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.domain.repository.ServiceEntryRepository
import com.vehicare.domain.ServiceSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The vehicle Detail's only service read: **one aggregate** per vehicle — entry count and the
 * newest service date — so the summary row costs one query however long the history is. There is
 * deliberately no per-entry read path here (Pitfall 4 / T-02-06-01).
 *
 * [summary] starts at `null`, which means "not resolved yet (or unreadable)" and is what makes the
 * row render its title alone. A resolved [ServiceSummary] with `entryCount == 0` is a different,
 * fully loaded state. A read failure also resolves to `null` rather than a fourth surface: the row
 * stays tappable either way.
 *
 * Only a count and a date cross this boundary; no entry field is logged (T-02-06-04).
 */
class ServiceSummaryViewModel(
    repo: ServiceEntryRepository,
    vehicleId: Long,
) : ViewModel() {

    val summary: StateFlow<ServiceSummary?> =
        repo.observeSummary(vehicleId)
            // Widen to nullable first: an unreadable aggregate resolves to `null` (= the row shows
            // its title alone), which a non-null flow cannot emit.
            .map<ServiceSummary, ServiceSummary?> { it }
            .catch { emit(null) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )
}
