package io.github.esseckers.pursuitofwind.feature.spots.di

import io.github.esseckers.pursuitofwind.feature.spots.data.repository.SpotsRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.spots.domain.repository.SpotsRepository
import io.github.esseckers.pursuitofwind.feature.spots.presentation.viewmodel.SpotsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The Spots tab: its repository over the shared data layer, and its ViewModel. */
internal val spotsModule: Module =
    module {
        factory<SpotsRepository> { SpotsRepositoryImpl(get()) }
        viewModel { SpotsViewModel(repository = get()) }
    }
