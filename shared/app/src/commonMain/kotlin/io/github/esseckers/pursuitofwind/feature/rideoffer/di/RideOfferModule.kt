package io.github.esseckers.pursuitofwind.feature.rideoffer.di

import io.github.esseckers.pursuitofwind.feature.rideoffer.data.repository.RideOfferRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.model.RideDraft
import io.github.esseckers.pursuitofwind.feature.rideoffer.domain.repository.RideOfferRepository
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.mapper.RideOfferStateMapper
import io.github.esseckers.pursuitofwind.feature.rideoffer.presentation.viewmodel.RideOfferViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Offering a ride from a GO window. The ViewModel takes the [RideDraft] as a parameter. */
internal val rideOfferModule: Module =
    module {
        factory<RideOfferRepository> { RideOfferRepositoryImpl(get(), get(), get()) }
        viewModel { (draft: RideDraft) ->
            val zone = TimeZone.currentSystemDefault()
            RideOfferViewModel(draft, repository = get(), mapper = RideOfferStateMapper.at(draft, get(), zone), clock = get())
        }
    }
