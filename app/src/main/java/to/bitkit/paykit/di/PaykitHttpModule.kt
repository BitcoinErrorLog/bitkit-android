package to.bitkit.paykit.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for Paykit-specific OkHttpClient
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PaykitOkHttp

/**
 * HTTP module for Paykit-specific networking.
 * Provides a shared OkHttpClient for Pubky storage operations.
 */
@Module
@InstallIn(SingletonComponent::class)
object PaykitHttpModule {

    @Provides
    @Singleton
    @PaykitOkHttp
    fun providePaykitOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
