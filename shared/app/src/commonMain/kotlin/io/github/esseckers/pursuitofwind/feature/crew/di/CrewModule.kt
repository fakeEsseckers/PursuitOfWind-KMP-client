package io.github.esseckers.pursuitofwind.feature.crew.di

import io.github.esseckers.pursuitofwind.feature.crew.data.repository.CrewsRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.crew.domain.repository.CrewsRepository
import io.github.esseckers.pursuitofwind.feature.crew.presentation.viewmodel.CrewViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The Crew tab: its repository over the shared data layer, and its ViewModel. The share sheet is a platform binding. */
internal val crewModule: Module =
    module {
        factory<CrewsRepository> { CrewsRepositoryImpl(get(), get(), get(), get()) }
        viewModel { CrewViewModel(repository = get(), clock = get(), riderZone = get()) }
    }
