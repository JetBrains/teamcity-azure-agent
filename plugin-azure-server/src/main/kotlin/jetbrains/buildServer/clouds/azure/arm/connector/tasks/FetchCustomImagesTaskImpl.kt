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

import com.microsoft.azure.management.compute.OperatingSystemStateTypes
import com.microsoft.azure.management.compute.OperatingSystemTypes
import jetbrains.buildServer.clouds.azure.arm.throttler.AzureThrottlerCacheableTaskBaseImpl
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
    override fun createQuery(api: AzureApi, parameter: Unit): Single<List<CustomImageTaskImageDescriptor>> {
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
                                            }).concatWith(Observable.just(
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
                                            ))
                                }
                )
                .toList()
                .last()
                .toSingle()
    }
}
