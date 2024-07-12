package jetbrains.buildServer.clouds.azure.arm.web.update.handler

enum class ImageUpdateType(private val myType: String) {
    UPSERT("upsert"),
    DELETE("delete");

    companion object {
        private val reverseValuesMap = ImageUpdateType.values()
            .associateBy(ImageUpdateType::myType)

        fun fromValue(type: String) = reverseValuesMap[type] ?: throw IllegalStateException("Unknown update type $type")
    }
}
