package to.bitkit.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import to.bitkit.data.AppDb
import to.bitkit.data.dao.TransferDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DbModule {

    @Provides
    @Singleton
    fun provideAppDb(
        @ApplicationContext applicationContext: Context,
    ): AppDb {
        return AppDb.getInstance(applicationContext)
    }

    @Provides
    @Singleton
    fun provideTransferDao(db: AppDb): TransferDao {
        return db.transferDao()
    }
}
