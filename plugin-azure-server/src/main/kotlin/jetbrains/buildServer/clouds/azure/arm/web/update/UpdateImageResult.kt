package jetbrains.buildServer.clouds.azure.arm.web.update

import jetbrains.buildServer.controllers.ActionErrors
import jetbrains.buildServer.serverSide.crypt.RSACipher
import org.jdom.Element

const val PASSWORDS_DATA = "passwords_data"

data class UpdateImageResult(private val value: String?) {
    private val errors = ActionErrors()

    fun registerErrors(error: ActionError) {
        errors.addError(error.id, error.message)
    }

    fun asElement(xmlResponse: Element): Element {
        if (errors.hasErrors()) {
            errors.serialize(xmlResponse)
        } else {
            assembleResponse(value, xmlResponse)
        }

        return xmlResponse
    }

    private fun assembleResponse(data: String?, xmlResponse: Element) {
        val encrypted = RSACipher.encryptDataForWeb(data)
        xmlResponse.addContent(Element(PASSWORDS_DATA)
            .apply { text = encrypted }
        )
    }
}
