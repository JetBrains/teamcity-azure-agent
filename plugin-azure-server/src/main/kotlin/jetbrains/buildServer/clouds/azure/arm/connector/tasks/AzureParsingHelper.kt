

package jetbrains.buildServer.clouds.azure.arm.connector.tasks

object AzureParsingHelper {
    fun getValueFromIdByName(id: String?, name: String?): String? {
        return id?.let {
            val parts = it.split("/").iterator()
            while (parts.hasNext()) {
                val part = parts.next()
                if (part.trim { it <= ' ' } !== "") {
                    if (part.equals(name, ignoreCase = true)) {
                        return if (parts.hasNext()) {
                            parts.next()
                        } else {
                            null
                        }
                    }
                }
            }
            return null
        }
    }
}
