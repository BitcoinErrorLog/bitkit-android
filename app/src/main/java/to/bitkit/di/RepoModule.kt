package to.bitkit.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import to.bitkit.repositories.AmountInputHandler
import to.bitkit.repositories.CurrencyRepo
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {

    @Suppress("unused")
    @Binds
    abstract fun bindAmountInputHandler(currencyRepo: CurrencyRepo): AmountInputHandler

    companion object {
        @Suppress("FunctionOnlyReturningConstant")
        @Provides
        @Named("enablePolling")
        fun provideEnablePolling(): Boolean = true
    }
}
