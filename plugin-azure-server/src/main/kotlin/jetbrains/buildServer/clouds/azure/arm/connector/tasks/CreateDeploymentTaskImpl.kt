/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.resources.Deployment
import com.microsoft.azure.management.resources.DeploymentMode
import com.microsoft.azure.management.resources.DeploymentProperties
import com.microsoft.azure.management.resources.fluentcore.utils.Utils
import com.microsoft.azure.management.resources.implementation.DeploymentExtendedInner
import com.microsoft.azure.management.resources.implementation.DeploymentInner
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskNotifications
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerTaskBaseImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_TASKS_CTREATEDEPLOYMENT_USE_MILTITHREAD_POLLING
import jetbrains.buildServer.serverSide.TeamCityProperties
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*
import rx.Observable
import rx.Single
import java.util.*

data class CreateDeploymentTaskParameter(
        val groupName: String,
        val deploymentName: String,
        val template: String,
        val params: String,
        val tags: Map<String, String>)

class CreateDeploymentTaskImpl(private val myNotifications: AzureTaskNotifications) : AzureThrottlerTaskBaseImpl<AzureApi, CreateDeploymentTaskParameter, Unit>() {
    private val objectMapper = ObjectMapper()

    override fun create(api: AzureApi, taskContext: AzureTaskContext, parameter: CreateDeploymentTaskParameter): Single<Unit> {
        if (TeamCityProperties.getBooleanOrTrue(TEAMCITY_CLOUDS_AZURE_TASKS_CTREATEDEPLOYMENT_USE_MILTITHREAD_POLLING)) {
            val managerClient = api.deploymentsClient()

            LOG.debug("Starting deployment. Name: ${parameter.deploymentName}, corellationId: [${taskContext.corellationId}]")

            val service = managerClient.azureClient.retrofit().create(DeploymentService::class.java)
            return managerClient
                .azureClient
                .putOrPatchAsync<DeploymentExtendedInner>(service.createOrUpdate(
                    parameter.groupName,
                    parameter.deploymentName,
                    api.subscriptionId(),
                    getDeploymentInner(parameter),
                    managerClient.apiVersion(),
                    managerClient.acceptLanguage(),
                    managerClient.userAgent()
                )) {
                    taskContext.getDeferralSequence()
                }
                .doOnNext {
                    myNotifications.raise(AzureTaskDeploymentStatusChangedEventArgs(
                        api,
                        it.id(),
                        it.name(),
                        it.properties().provisioningState(),
                        it.properties().providers(),
                        it.properties().dependencies(),
                        taskContext
                    ))
                }
                .map { Unit }
                .toSingle()

        } else {
            return Utils.rootResource<Deployment>(
                api
                    .deployments()
                    .define(parameter.deploymentName)
                    .withExistingResourceGroup(parameter.groupName)
                    .withTemplate(parameter.template)
                    .withParameters(parameter.params)
                    .withMode(DeploymentMode.INCREMENTAL)
                    .createAsync()
            )
                .doOnNext {
                    val inner = it.inner()
                    myNotifications.raise(AzureTaskDeploymentStatusChangedEventArgs(
                        api,
                        inner.id(),
                        inner.name(),
                        inner.properties().provisioningState(),
                        inner.properties().providers(),
                        inner.properties().dependencies(),
                        taskContext
                    ))
                }
                .map { Unit }
                .toSingle()
        }
    }

    private fun getDeploymentInner(parameter: CreateDeploymentTaskParameter): DeploymentInner {
        val deploymentProperties = DeploymentProperties()
        deploymentProperties
            .withTemplate(objectMapper.readValue(parameter.template, Any::class.java))
            .withParameters(objectMapper.readValue(parameter.params, Any::class.java))
            .withMode(DeploymentMode.INCREMENTAL)

        val deploymentInner = DeploymentInner()
        deploymentInner.withProperties(deploymentProperties)
        deploymentInner.withTags(parameter.tags)

        return deploymentInner
    }

    interface DeploymentService {
        @Headers("Content-Type: application/json; charset=utf-8")
        @PUT("subscriptions/{subscriptionId}/resourcegroups/{resourceGroupName}/providers/Microsoft.Resources/deployments/{deploymentName}")
        fun createOrUpdate(
            @Path("resourceGroupName") resourceGroupName: String,
            @Path("deploymentName") deploymentName: String,
            @Path("subscriptionId") subscriptionId: String?,
            @Body parameters: DeploymentInner,
            @Query("api-version") apiVersion: String,
            @Header("accept-language") acceptLanguage: String,
            @Header("User-Agent") userAgent: String
        ): Observable<Response<ResponseBody>>
    }

    companion object {
        private val LOG = Logger.getInstance(CreateDeploymentTaskImpl::class.java.name)
    }
}
