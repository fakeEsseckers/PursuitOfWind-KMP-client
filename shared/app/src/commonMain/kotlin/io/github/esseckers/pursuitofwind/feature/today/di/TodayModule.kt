package io.github.esseckers.pursuitofwind.feature.today.di

import io.github.esseckers.pursuitofwind.feature.today.data.repository.TodayRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.today.domain.repository.TodayRepository
import io.github.esseckers.pursuitofwind.feature.today.presentation.viewmodel.TodayViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The Today feature: its repository over the shared data layer, and its ViewModel. */
internal val todayModule: Module =
    module {
        factory<TodayRepository> { TodayRepositoryImpl(get(), get(), get(), get(), get(), get()) }
        viewModel { TodayViewModel(repository = get(), clock = get(), riderZone = get()) }
    }
