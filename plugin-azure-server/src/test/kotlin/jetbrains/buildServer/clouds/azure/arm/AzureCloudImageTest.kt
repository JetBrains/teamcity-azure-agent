

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.azure.arm.AzureCloudDeployTarget
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImage
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageDetails
import jetbrains.buildServer.clouds.azure.arm.AzureCloudImageType
import jetbrains.buildServer.clouds.azure.arm.AzureCloudInstance
import jetbrains.buildServer.clouds.azure.arm.AzureInstanceEventListener
import jetbrains.buildServer.clouds.azure.arm.connector.AzureApiConnector
import jetbrains.buildServer.clouds.azure.arm.connector.AzureInstance
import junit.framework.TestCase
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jmock.MockObjectTestCase
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.concurrent.CyclicBarrier
import kotlin.concurrent.thread

class AzureCloudImageTest : MockObjectTestCase() {
    private lateinit var myJob: CompletableJob
    private lateinit var myImageDetails: AzureCloudImageDetails
    private lateinit var myApiConnector: AzureApiConnector
    private lateinit var myScope: CoroutineScope
    private lateinit var myInstanceListener: AzureInstanceEventListener

    @BeforeMethod
    fun beforeMethod() {
        MockKAnnotations.init(this)

        myApiConnector = mockk();
        coEvery { myApiConnector.createInstance(any(), any()) } coAnswers {
            val instance = firstArg<AzureCloudInstance>()
            instance.hasVmInstance = true
            Unit
        }
        every { myApiConnector.fetchInstances<AzureInstance>(any<AzureCloudImage>()) } returns emptyMap()
        coEvery { myApiConnector.stopInstance(any()) } coAnswers {
            val instance = firstArg<AzureCloudInstance>()
            TestCase.assertEquals(instance.hasVmInstance, true)

            instance.status = InstanceStatus.STOPPED
            Unit
        }

        myImageDetails = AzureCloudImageDetails(
            mySourceId = null,
            deployTarget = AzureCloudDeployTarget.SpecificGroup,
            regionId = "regionId",
            groupId = "groupId",
            imageType = AzureCloudImageType.Image,
            imageUrl = null,
            imageId = "imageId",
            instanceId = null,
            osType = null,
            networkId = null,
            subnetId = null,
            vmNamePrefix = "vm",
            vmSize = null,
            vmPublicIp = null,
            myMaxInstances = 2,
            username = null,
            storageAccountType = null,
            template = null,
            numberCores = null,
            memory = null,
            storageAccount = null,
            registryUsername = null,
            agentPoolId = null,
            profileId = null,
            myReuseVm = false,
            customEnvironmentVariables = null,
            spotVm = null,
            enableSpotPrice = null,
            spotPrice = null,
            enableAcceleratedNetworking = null,
            disableTemplateModification = null,
            userAssignedIdentity = null,
            enableSystemAssignedIdentity = null
        )

        myJob = SupervisorJob()
        myScope = CoroutineScope(myJob + Dispatchers.IO)
        myInstanceListener = mockk(relaxed = true)
    }

    @Test
    fun shouldCreateNewInstance() {
        // Given
        myScope = CoroutineScope(Dispatchers.Unconfined)
        val instance = createInstance()
        val userData = CloudInstanceUserData(
                "agentName",
                "authToken",
                "",
                0,
                "profileId",
                "profileDescr",
                emptyMap()
        )

        // When
        instance.startNewInstance(userData)

        // Then
        coVerify { myApiConnector.createInstance(
                match { i ->
                    i.imageId == instance.id &&
                    i.name == myImageDetails.vmNamePrefix!!.lowercase() + "1" &&
                    i.image == instance &&
                    i.status == InstanceStatus.STARTING
                },
                match { userData ->
                    userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "1" &&
                    userData.profileId == "profileId" &&
                    userData.profileDescription == "profileDescr"
                })
        }
    }

    @Test
    fun shouldCreateSecondInstance() {
        // Given
        myScope = CoroutineScope(Dispatchers.Unconfined)
        val instance = createInstance()
        val userData = CloudInstanceUserData(
                "agentName",
                "authToken",
                "",
                0,
                "profileId",
                "profileDescr",
                emptyMap()
        )

        instance.startNewInstance(userData)

        // When
        instance.startNewInstance(userData)

        // Then
        coVerify { myApiConnector.createInstance(
                match { i ->
                    i.imageId == instance.id &&
                            i.name == myImageDetails.vmNamePrefix!!.lowercase() + "2" &&
                            i.image == instance &&
                            i.status == InstanceStatus.STARTING
                },
                match { userData ->
                    userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "2" &&
                            userData.profileId == "profileId" &&
                            userData.profileDescription == "profileDescr"
                })
        }
    }

    @Test(invocationCount = 30)
    fun shouldCreateSecondInstanceInParallel() {
        // Given
        val instance = createInstance()
        val userData = CloudInstanceUserData(
                "agentName",
                "authToken",
                "",
                0,
                "profileId",
                "profileDescr",
                emptyMap()
        )
        val barrier = CyclicBarrier(3)

        // When
        val thread1 = thread(start = true) { barrier.await(); instance.startNewInstance(userData) }
        val thread2 = thread(start = true) { barrier.await(); instance.startNewInstance(userData) }

        barrier.await()

        thread1.join()
        thread2.join()

        myJob.complete()
        runBlocking { myJob.join() }

        // Then
        coVerify { myApiConnector.createInstance(
                match { i ->
                    i.imageId == instance.id &&
                            i.name == myImageDetails.vmNamePrefix!!.lowercase() + "1" &&
                            i.image == instance &&
                            i.status == InstanceStatus.STARTING
                },
                match { userData ->
                    userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "1" &&
                            userData.profileId == "profileId" &&
                            userData.profileDescription == "profileDescr"
                })
        }

        coVerify { myApiConnector.createInstance(
                match { i ->
                    i.imageId == instance.id &&
                            i.name == myImageDetails.vmNamePrefix!!.lowercase() + "2" &&
                            i.image == instance &&
                            i.status == InstanceStatus.STARTING
                },
                match { userData ->
                    userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "2" &&
                            userData.profileId == "profileId" &&
                            userData.profileDescription == "profileDescr"
                })
        }
    }

    @Test(invocationCount = 30)
    fun shouldCheckInstanceLimitWhenCreateInstanceInParallel() {
        // Given
        val instance = createInstance()
        val userData = CloudInstanceUserData(
                "agentName",
                "authToken",
                "",
                0,
                "profileId",
                "profileDescr",
                emptyMap()
        )
        val barrier = CyclicBarrier(4)

        // When
        val thread1 = thread(start = true) { barrier.await(); runBlocking { instance.startNewInstance(userData) } }
        val thread2 = thread(start = true) { barrier.await(); runBlocking { instance.startNewInstance(userData) } }
        val thread3 = thread(start = true) { barrier.await(); runBlocking { instance.startNewInstance(userData) } }

        barrier.await()

        thread1.join()
        thread2.join()
        thread3.join()

        myJob.complete()
        runBlocking { myJob.join() }

        // Then
        coVerify { myApiConnector.createInstance(
                match { i ->
                    i.imageId == instance.id &&
                            i.name == myImageDetails.vmNamePrefix!!.lowercase() + "1" &&
                            i.image == instance &&
                            i.status == InstanceStatus.STARTING
                },
                match { userData ->
                    userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "1" &&
                            userData.profileId == "profileId" &&
                            userData.profileDescription == "profileDescr"
                })
        }

        coVerify { myApiConnector.createInstance(
                match { i ->
                    i.imageId == instance.id &&
                            i.name == myImageDetails.vmNamePrefix!!.lowercase() + "2" &&
                            i.image == instance &&
                            i.status == InstanceStatus.STARTING
                },
                match { userData ->
                    userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "2" &&
                            userData.profileId == "profileId" &&
                            userData.profileDescription == "profileDescr"
                })
        }

        coVerify(exactly = 0) { myApiConnector.createInstance(
                match { i ->
                    i.imageId == instance.id &&
                            i.name == myImageDetails.vmNamePrefix!!.lowercase() + "3" &&
                            i.image == instance &&
                            i.status == InstanceStatus.STARTING
                },
                match { userData ->
                    userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "3" &&
                            userData.profileId == "profileId" &&
                            userData.profileDescription == "profileDescr"
                })
        }
    }

    @Test(invocationCount = 100)
    fun shouldStartStoppedInstancesInParallel() {
        // Given
        myImageDetails = AzureCloudImageDetails(
            mySourceId = null,
            deployTarget = AzureCloudDeployTarget.SpecificGroup,
            regionId = "regionId",
            groupId = "groupId",
            imageType = AzureCloudImageType.Image,
            imageUrl = null,
            imageId = "imageId",
            instanceId = null,
            osType = null,
            networkId = null,
            subnetId = null,
            vmNamePrefix = "vm",
            vmSize = null,
            vmPublicIp = null,
            myMaxInstances = 2,
            username = null,
            storageAccountType = null,
            template = null,
            numberCores = null,
            memory = null,
            storageAccount = null,
            registryUsername = null,
            agentPoolId = null,
            profileId = null,
            myReuseVm = true,
            customEnvironmentVariables = null,
            spotVm = null,
            enableSpotPrice = null,
            spotPrice = null,
            enableAcceleratedNetworking = null,
            disableTemplateModification = null,
            userAssignedIdentity = null,
            enableSystemAssignedIdentity = null
        )

        coEvery { myApiConnector.startInstance(any<AzureCloudInstance>(), any<CloudInstanceUserData>()) } coAnswers {
            val instance = firstArg<AzureCloudInstance>()
            instance.status = InstanceStatus.RUNNING
            Unit
        }

        val instance = createInstance()
        val userData = CloudInstanceUserData(
            "agentName",
            "authToken",
            "",
            0,
            "profileId",
            "profileDescr",
            emptyMap()
        )

        val stoppedInstance = runBlocking(myJob) {
            instance.startNewInstance(userData)
        }

        runBlocking(myJob) {
            instance.terminateInstance(stoppedInstance)
        }

        val barrier = CyclicBarrier(3)

        // When
        val thread1 = thread(start = true) { barrier.await(); instance.startNewInstance(userData); }
        val thread2 = thread(start = true) { barrier.await(); instance.startNewInstance(userData); }

        barrier.await()

        thread1.join()
        thread2.join()

        myJob.complete()
        runBlocking { myJob.join() }

        // Then
        coVerify(exactly = 1) {
            myApiConnector.createInstance(
                match { i ->
                    i.imageId == instance.id &&
                            i.name == myImageDetails.vmNamePrefix!!.lowercase() + "1" &&
                            i.image == instance
                },
                match { userData ->
                    userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "1" &&
                            userData.profileId == "profileId" &&
                            userData.profileDescription == "profileDescr"
                })
        }

        coVerify(exactly = 1) { myApiConnector.createInstance(
            match { i ->
                i.imageId == instance.id &&
                        i.name == myImageDetails.vmNamePrefix!!.lowercase() + "2" &&
                        i.image == instance
            },
            match { userData ->
                userData.agentName == myImageDetails.vmNamePrefix!!.lowercase() + "2" &&
                        userData.profileId == "profileId" &&
                        userData.profileDescription == "profileDescr"
            })
        }

        coVerify(exactly = 2) {myApiConnector.createInstance(any(), any()) }

        coVerify { myApiConnector.startInstance(any(), userData) }
        coVerify(exactly = 1) { myApiConnector.startInstance(eq(stoppedInstance), userData) }

        verify(exactly = 1) { myInstanceListener.instanceTerminated(any()) }
        verify { myInstanceListener.instanceTerminated(eq(stoppedInstance)) }
    }

    private fun<T> runBlocking(job : CompletableJob, action: () -> T) : T {
        val result = action()
        runBlocking { job.children.forEach { it.join() } }
        return result
    }

    private fun createInstance() : AzureCloudImage {
        return AzureCloudImage(myImageDetails, myApiConnector, myScope, myInstanceListener)
    }
}
