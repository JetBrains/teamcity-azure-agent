package jetbrains.buildServer.clouds.azure.arm.web.update.handler

enum class ImageUpdateType(private val myType: String) {
    UPDATE("update"),
    DELETE("delete"),
    ADD("add");

    companion object {
        private val reverseValuesMap = ImageUpdateType.values()
            .associateBy(ImageUpdateType::name)

        fun fromValue(type: String) = reverseValuesMap[type] ?: throw IllegalStateException("Unknown update type $type")
    }
}
