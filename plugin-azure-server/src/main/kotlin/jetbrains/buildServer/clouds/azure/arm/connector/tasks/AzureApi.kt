package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.compute.Galleries
import com.microsoft.azure.management.compute.VirtualMachineCustomImages
import com.microsoft.azure.management.compute.VirtualMachines
import com.microsoft.azure.management.containerinstance.ContainerGroups
import com.microsoft.azure.management.network.Networks
import com.microsoft.azure.management.network.PublicIPAddresses
import com.microsoft.azure.management.resources.Deployments
import com.microsoft.azure.management.resources.GenericResources
import com.microsoft.azure.management.resources.Providers
import com.microsoft.azure.management.resources.ResourceGroups
import com.microsoft.azure.management.resources.Subscriptions
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl
import com.microsoft.azure.management.storage.StorageAccounts
import jetbrains.buildServer.clouds.azure.arm.resourceGraph.ResourceGraph
import jetbrains.buildServer.clouds.azure.arm.virtualMachinesEx.VirtualMachinesEx

interface AzureApi {
    fun subscriptionId(): String?

    fun deployments(): Deployments

    fun resourceGroups(): ResourceGroups

    fun virtualMachines(): VirtualMachines

    fun virtualMachinesEx(): VirtualMachinesEx

    fun containerGroups(): ContainerGroups

    fun networks(): Networks

    fun genericResources(): GenericResources

    fun virtualMachineCustomImages(): VirtualMachineCustomImages

    fun galleries(): Galleries

    fun publicIPAddresses(): PublicIPAddresses

    fun subscriptions(): Subscriptions

    fun providers(): Providers

    fun storageAccounts(): StorageAccounts

    fun resourceGraph(): ResourceGraph

    fun deploymentsClient(): ResourceManagementClientImpl
}
