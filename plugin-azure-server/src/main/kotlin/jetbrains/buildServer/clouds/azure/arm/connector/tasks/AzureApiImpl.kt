package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.compute.Galleries
import com.microsoft.azure.management.compute.VirtualMachineCustomImages
import com.microsoft.azure.management.compute.VirtualMachines
import com.microsoft.azure.management.containerinstance.ContainerGroups
import com.microsoft.azure.management.network.Networks
import com.microsoft.azure.management.network.PublicIPAddresses
import com.microsoft.azure.management.resources.*
import com.microsoft.azure.management.resources.implementation.ResourceManagementClientImpl
import com.microsoft.azure.management.storage.StorageAccounts
import jetbrains.buildServer.clouds.azure.arm.resourceGraph.ResourceGraph
import jetbrains.buildServer.clouds.azure.arm.virtualMachinesEx.VirtualMachinesEx
import jetbrains.buildServer.clouds.azure.arm.virtualMachinesEx.VirtualMachinesExInner

class AzureApiImpl(
    private val api: Azure,
    private val resourceGrapgApi: ResourceGraph
) : AzureApi {
    private val virtualMachinesEx = VirtualMachinesEx(VirtualMachinesExInner(api.virtualMachines().manager().inner()))
    override fun subscriptionId(): String? = api.subscriptionId()

    override fun deployments(): Deployments = api.deployments()

    override fun resourceGroups(): ResourceGroups = api.resourceGroups()

    override fun virtualMachines(): VirtualMachines = api.virtualMachines()

    override fun virtualMachinesEx(): VirtualMachinesEx = virtualMachinesEx

    override fun containerGroups(): ContainerGroups = api.containerGroups()

    override fun networks(): Networks = api.networks()

    override fun genericResources(): GenericResources = api.genericResources()

    override fun virtualMachineCustomImages(): VirtualMachineCustomImages = api.virtualMachineCustomImages()

    override fun galleries(): Galleries = api.galleries()

    override fun publicIPAddresses(): PublicIPAddresses = api.publicIPAddresses()

    override fun subscriptions(): Subscriptions = api.subscriptions()

    override fun providers(): Providers = api.providers()

    override fun storageAccounts(): StorageAccounts = api.storageAccounts()

    override fun resourceGraph(): ResourceGraph = resourceGrapgApi

    override fun deploymentsClient(): ResourceManagementClientImpl = deployments().manager().inner()
}
