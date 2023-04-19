package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.compute.Galleries
import com.microsoft.azure.management.compute.VirtualMachineCustomImages
import com.microsoft.azure.management.compute.VirtualMachines
import com.microsoft.azure.management.containerinstance.ContainerGroups
import com.microsoft.azure.management.network.Networks
import com.microsoft.azure.management.network.PublicIPAddresses
import com.microsoft.azure.management.resources.*
import com.microsoft.azure.management.storage.StorageAccounts

interface AzureApi {
    fun subscriptionId(): String?

    fun deployments(): Deployments

    fun resourceGroups(): ResourceGroups

    fun virtualMachines(): VirtualMachines

    fun containerGroups(): ContainerGroups

    fun networks(): Networks

    fun genericResources(): GenericResources

    fun virtualMachineCustomImages(): VirtualMachineCustomImages

    fun galleries(): Galleries

    fun publicIPAddresses(): PublicIPAddresses

    fun subscriptions(): Subscriptions

    fun providers(): Providers

    fun storageAccounts(): StorageAccounts
}
