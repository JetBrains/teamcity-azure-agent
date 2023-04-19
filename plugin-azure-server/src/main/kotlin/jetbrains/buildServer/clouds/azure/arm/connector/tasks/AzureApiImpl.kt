package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.Galleries
import com.microsoft.azure.management.compute.VirtualMachineCustomImages
import com.microsoft.azure.management.compute.VirtualMachines
import com.microsoft.azure.management.containerinstance.ContainerGroups
import com.microsoft.azure.management.network.Networks
import com.microsoft.azure.management.network.PublicIPAddresses
import com.microsoft.azure.management.resources.*
import com.microsoft.azure.management.storage.StorageAccounts

class AzureApiImpl(
    private val api: Azure
) : AzureApi {
    override fun subscriptionId(): String? = api.subscriptionId()

    override fun deployments(): Deployments = api.deployments()

    override fun resourceGroups(): ResourceGroups = api.resourceGroups()

    override fun virtualMachines(): VirtualMachines = api.virtualMachines()

    override fun containerGroups(): ContainerGroups = api.containerGroups()

    override fun networks(): Networks = api.networks()

    override fun genericResources(): GenericResources = api.genericResources()

    override fun virtualMachineCustomImages(): VirtualMachineCustomImages = api.virtualMachineCustomImages()

    override fun galleries(): Galleries = api.galleries()

    override fun publicIPAddresses(): PublicIPAddresses = api.publicIPAddresses()

    override fun subscriptions(): Subscriptions = api.subscriptions()

    override fun providers(): Providers = api.providers()

    override fun storageAccounts(): StorageAccounts = api.storageAccounts()
}
