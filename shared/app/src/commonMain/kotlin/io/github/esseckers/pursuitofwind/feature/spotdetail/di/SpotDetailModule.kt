package io.github.esseckers.pursuitofwind.feature.spotdetail.di

import io.github.esseckers.pursuitofwind.engine.model.SpotId
import io.github.esseckers.pursuitofwind.feature.spotdetail.data.repository.SpotDetailRepositoryImpl
import io.github.esseckers.pursuitofwind.feature.spotdetail.domain.repository.SpotDetailRepository
import io.github.esseckers.pursuitofwind.feature.spotdetail.presentation.viewmodel.SpotDetailViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The spot detail feature. The ViewModel takes the spot and the day to show as parameters. */
internal val spotDetailModule: Module =
    module {
        factory<SpotDetailRepository> { SpotDetailRepositoryImpl(get(), get(), get()) }
        viewModel { (spotId: SpotId, date: LocalDate?) ->
            SpotDetailViewModel(
                spotId = spotId,
                date = date,
                repository = get(),
                clock = get(),
                riderZone = TimeZone.currentSystemDefault(),
            )
        }
    }
