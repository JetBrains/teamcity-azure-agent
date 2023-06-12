package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.google.common.reflect.TypeToken
import com.microsoft.azure.AzureClient
import com.microsoft.azure.CloudException
import com.microsoft.azure.LongRunningOperationOptions
import com.microsoft.azure.PollingState
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_COUNT
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_TIMEOUT
import jetbrains.buildServer.serverSide.TeamCityProperties
import okhttp3.ResponseBody
import retrofit2.Response
import rx.Observable
import rx.Single
import rx.exceptions.Exceptions
import rx.schedulers.Schedulers
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.TimeUnit

inline fun <reified T> AzureClient.putOrPatchAsync(source: Observable<Response<ResponseBody>>, noinline beforePollAttemptHandler: () -> Unit): Observable<T> {
    val resourceType = object : TypeToken<T>() {}.type
    return pollAsync(this, this.beginPutOrPatchAsync<T>(source, resourceType), resourceType, beforePollAttemptHandler)
}

inline fun <reified T> AzureClient.postOrDeleteAsync(source: Observable<Response<ResponseBody>>, options: LongRunningOperationOptions, noinline beforePollAttemptHandler: () -> Unit): Observable<T> {
    val resourceType = object : TypeToken<T>() {}.type
    return pollAsync(this, this.beginPostOrDeleteAsync(source, options, resourceType), resourceType, beforePollAttemptHandler)
}

public fun<T> pollAsync(azureClient: AzureClient, pollingStateSource: Single<PollingState<T>>, resourceType: Type, beforePollAttemptHandler: () -> Unit): Observable<T> =
    pollingStateSource
        .toObservable()
        .flatMap { state ->
            if (isStatusTerminal(state)) {
                Observable.just(state)
            } else {
                Observable
                    .just(state)
                    .delaySubscription(
                        TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_TIMEOUT, 30),
                        TimeUnit.SECONDS,
                        Schedulers.computation()
                    )
                    .observeOn(Schedulers.io())
            }
        }
        .flatMap {
            doPollAsync(azureClient, it, resourceType, beforePollAttemptHandler)
        }
        .last()
        .map {
            it.resource()
        }

public fun <T> doPollAsync(azureClient: AzureClient, state: PollingState<T>, resourceType: Type, beforePollAttemptHandler: () -> Unit): Observable<PollingState<T>> {
    val retryCount = TeamCityProperties.getInteger(TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_COUNT, 5)
    return Observable.just(true)
        .flatMap<PollingState<T>> {
            beforePollAttemptHandler()
            azureClient.pollSingleAsync(state, resourceType).toObservable()
        }
        .repeatWhen { observable ->
            observable.flatMap {
                Observable.timer(
                    TeamCityProperties.getLong(TEAMCITY_CLOUDS_AZURE_DEPLOYMENT_LONG_RUNNING_QUERY_RETRY_TIMEOUT, 30),
                    TimeUnit.SECONDS,
                    Schedulers.computation()
                )
                .observeOn(Schedulers.io())
            }
        }
        .retryWhen { observable ->
            observable.zipWith(Observable.range(1, retryCount)) { throwable, integer ->
                if (throwable is CloudException || integer == retryCount) {
                    throw Exceptions.propagate(throwable)
                }
                integer
            }
        }
        .takeUntil { isStatusTerminal(state) }
}

public fun <T> isStatusTerminal(state: PollingState<T>): Boolean =
    setOf("failed", "canceled", "succeeded").contains(state.status().lowercase(Locale.getDefault()))
