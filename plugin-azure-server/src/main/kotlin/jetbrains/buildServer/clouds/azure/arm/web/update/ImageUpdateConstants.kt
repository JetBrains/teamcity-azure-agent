package jetbrains.buildServer.clouds.azure.arm.web.update

class ImageUpdateConstants {

    val updateImageRequestPath: String
        get() = UPDATE_IMAGE_REQUEST_PATH

    companion object {
        const val UPDATE_IMAGE_REQUEST_PATH = "/plugins/cloud-azure-arm/images.html"
        const val IMAGE_UPDATE_TYPE = "imageUpdateType"
        const val PASSWORD = "secure:password"
        const val PASSWORDS_DATA = "secure:passwords_data"
    }
}
