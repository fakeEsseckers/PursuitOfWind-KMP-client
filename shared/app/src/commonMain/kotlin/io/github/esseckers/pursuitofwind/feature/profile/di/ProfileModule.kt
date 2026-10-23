package io.github.esseckers.pursuitofwind.feature.profile.di

import io.github.esseckers.pursuitofwind.feature.profile.data.repository.ProfileEditRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.profile.domain.repository.ProfileEditRepository
import io.github.esseckers.pursuitofwind.feature.profile.presentation.viewmodel.ProfileViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The profile form: its repository over the shared data layer, and its ViewModel (one per back stack entry). */
internal val profileModule: Module =
    module {
        factory<ProfileEditRepository> { ProfileEditRepositoryImpl(get()) }
        viewModel { ProfileViewModel(repository = get()) }
    }
