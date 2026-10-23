package io.github.esseckers.pursuitofwind.feature.spotpicker.di

import io.github.esseckers.pursuitofwind.feature.spotpicker.data.repository.SpotPickerRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.spotpicker.domain.repository.SpotPickerRepository
import io.github.esseckers.pursuitofwind.feature.spotpicker.presentation.viewmodel.SpotPickerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The spot picker: its repository over the shared data layer, and its ViewModel. */
internal val spotPickerModule: Module =
    module {
        factory<SpotPickerRepository> { SpotPickerRepositoryImpl(get()) }
        viewModel { SpotPickerViewModel(repository = get()) }
    }
