package io.github.esseckers.pursuitofwind.feature.spotmap.di

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotmap.data.repository.SpotMapRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.spotmap.domain.repository.SpotMapRepository
import io.github.esseckers.pursuitofwind.feature.spotmap.presentation.viewmodel.SpotMapViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The map of spots: its repository over the shared data layer, and its ViewModel, which takes the spot to change, if any. */
internal val spotMapModule: Module =
    module {
        factory<SpotMapRepository> { SpotMapRepositoryImpl(get(), get()) }
        viewModel { (editing: SpotId?) -> SpotMapViewModel(repository = get(), deviceZone = get(), editing = editing) }
    }
