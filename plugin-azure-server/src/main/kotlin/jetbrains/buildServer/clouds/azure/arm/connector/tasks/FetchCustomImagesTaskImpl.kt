

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

import com.intellij.openapi.diagnostic.Logger
import com.microsoft.azure.management.compute.OperatingSystemStateTypes
import com.microsoft.azure.management.compute.OperatingSystemTypes
import jetbrains.buildServer.clouds.azure.arm.resourceGraph.QueryRequest
import jetbrains.buildServer.clouds.azure.arm.resourceGraph.QueryResponse
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureTaskContext
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
import jetbrains.buildServer.clouds.azure.arm.throttler.TEAMCITY_CLOUDS_AZURE_TASKS_FETCHCUSTOMIMAGES_RESOURCEGRAPH_DISABLE
import jetbrains.buildServer.serverSide.TeamCityProperties
import rx.Observable
import rx.Single

data class CustomImageTaskImageDescriptor(
        val id: String,
        val name: String,
        val regionName: String,
        val osState: OperatingSystemStateTypes?,
        val osType: OperatingSystemTypes?,
        val galleryImageDescriptor: GalleryImageDescriptor?
)

data class GalleryImageDescriptor (
    val galleryId: String,
    val galleryName: String,
    val imageId: String,
    val imageName: String,
    val versionId: String?,
    val versionName: String
)

class FetchCustomImagesTaskImpl : AzureThrottlerCacheableTaskBaseImpl<Unit, List<CustomImageTaskImageDescriptor>>() {
    override fun createQuery(api: AzureApi, taskContext: AzureTaskContext, parameter: Unit): Single<List<CustomImageTaskImageDescriptor>> {
        if (TeamCityProperties.getBoolean(TEAMCITY_CLOUDS_AZURE_TASKS_FETCHCUSTOMIMAGES_RESOURCEGRAPH_DISABLE)) {
            return api
                .virtualMachineCustomImages()
                .listAsync()
                .map {
                    CustomImageTaskImageDescriptor(
                        it.id(),
                        it.name(),
                        it.regionName(),
                        it.osDiskImage()?.osState(),
                        it.osDiskImage()?.osType(),
                        null
                    )
                }
                .mergeWith(
                    api
                        .galleries()
                        .listAsync()
                        .flatMap { gallery -> gallery.listImagesAsync().map { image -> gallery to image } }
                        .flatMap { (gallery, image) -> image.listVersionsAsync().toList().map { versionList -> Triple(gallery, image, versionList) } }
                        .flatMap { (gallery, image, versions) ->
                            if (versions.isEmpty()) return@flatMap Observable.empty<CustomImageTaskImageDescriptor>()

                            val galleryName = AzureParsingHelper.getValueFromIdByName(image.id(), "galleries")
                            var imageName = galleryName + '/' + image.name()

                            Observable.from(
                                versions.map {
                                    val name = imageName + '/' + it.name()
                                    CustomImageTaskImageDescriptor(
                                        it.id(),
                                        name,
                                        image.location(),
                                        image.osState(),
                                        image.osType(),
                                        GalleryImageDescriptor(
                                            gallery.id(),
                                            gallery.name(),
                                            image.id(),
                                            image.name(),
                                            it.id(),
                                            it.name()
                                        )
                                    )
                                }).concatWith(
                                Observable.just(
                                    CustomImageTaskImageDescriptor(
                                        image.id(),
                                        imageName + "/latest",
                                        image.location(),
                                        image.osState(),
                                        image.osType(),
                                        GalleryImageDescriptor(
                                            gallery.id(),
                                            gallery.name(),
                                            image.id(),
                                            image.name(),
                                            null,
                                            "latest"
                                        )
                                    )
                                )
                            )
                        }
                )
                .toList()
                .last()
                .toSingle()
        }
        return api
            .resourceGraph()
            .resources()
            .resourcesAsync(QueryRequest(QUERY))
            .map { rsp ->
                if (rsp.resultTruncated == "true") {
                    LOG.warn("Galleries images response was truncated. TotalCount: ${rsp.totalRecords}, Count: ${rsp.count}")
                }

                val response = QueryResponse(rsp)

                val result = mutableListOf<CustomImageTaskImageDescriptor>()
                var previousImageId : String? = null
                response.table.rows.forEach {
                    val galleryId = it.getStringValue("galleryId")
                    val imageId = it.getStringValue("imageId", true)!!
                    val imageName = it.getStringValue("imageName", true)!!
                    val imageLocation = it.getStringValue("imageLocation", true)!!
                    val imageOsType = it.getStringValue("imageOsType")?.let { OperatingSystemTypes.fromString(it) }
                    val imageOsState = it.getStringValue("imageOsState").let { OperatingSystemStateTypes.fromString(it) }

                    if (galleryId != null) {
                        val galleryName = it.getStringValue("galleryName", true)!!
                        val versionId = it.getStringValue("versionId", true)!!
                        val versionName = it.getStringValue("versionName", true)!!
                        val name = galleryName + "/" + imageName

                        result.add(
                            CustomImageTaskImageDescriptor(
                                versionId,
                                name + "/" + versionName,
                                imageLocation,
                                imageOsState,
                                imageOsType,
                                GalleryImageDescriptor(
                                    galleryId,
                                    galleryName,
                                    imageId,
                                    imageName,
                                    versionId,
                                    versionName
                                )
                            )
                        )

                        if (previousImageId != imageId) {
                            result.add(
                                CustomImageTaskImageDescriptor(
                                    imageId,
                                    name + "/latest",
                                    imageLocation,
                                    imageOsState,
                                    imageOsType,
                                    GalleryImageDescriptor(
                                        galleryId,
                                        galleryName,
                                        imageId,
                                        imageName,
                                        null,
                                        "latest"
                                    )
                                )
                            )
                        }
                    } else {
                        result.add(
                            CustomImageTaskImageDescriptor(
                                imageId,
                                imageName,
                                imageLocation,
                                imageOsState,
                                imageOsType,
                                null
                            )
                        );
                    }

                    previousImageId = imageId
                }
                result.toList()
            }
            .last()
            .toSingle()
    }

    companion object {
        private const val QUERY = "resources\n" +
                "| where type =~ \"microsoft.compute/galleries\"\n" +
                "| project \n" +
                "    galleryId = id,\n" +
                "    galleryName = name\n" +
                "| join kind=inner (\n" +
                "    resources\n" +
                "    | where type =~ \"microsoft.compute/galleries/images\"\n" +
                "    | project \n" +
                "        imageId = id,\n" +
                "        galleryId = strcat_array(array_slice(split(id, '/'), 0, -3), '/'),\n" +
                "        imageName = name,\n" +
                "        imageLocation = location,\n" +
                "        imageOsType = properties.osType,\n" +
                "        imageOsState = properties.osState\n" +
                ") on galleryId\n" +
                "| project-away galleryId1\n" +
                "| join kind=inner (\n" +
                "    resources\n" +
                "    | where type =~ \"microsoft.compute/galleries/images/versions\"\n" +
                "    | project \n" +
                "        versionId = id,\n" +
                "        imageId = strcat_array(array_slice(split(id, '/'), 0, -3), '/'),\n" +
                "        versionName = name\n" +
                ") on imageId\n" +
                "| project-away imageId1\n" +
                "| union kind=outer (\n" +
                "    resources\n" +
                "    | where type == \"microsoft.compute/images\"\n" +
                "    | extend osDisk = properties.storageProfile.osDisk\n" +
                "    | project\n" +
                "        imageId = id,\n" +
                "        imageName = name,\n" +
                "        imageLocation = location,\n" +
                "        imageOsType = osDisk.osType,\n" +
                "        imageOsState = osDisk.osState\n" +
                "    )\n" +
                "| sort by galleryId, imageId, versionId"
        private val LOG = Logger.getInstance(FetchCustomImagesTaskImpl::class.java.name)
    }
}
