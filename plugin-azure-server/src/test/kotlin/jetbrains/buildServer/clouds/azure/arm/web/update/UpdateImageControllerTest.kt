package jetbrains.buildServer.clouds.azure.arm.web.update

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import jetbrains.buildServer.serverSide.SBuildServer
import jetbrains.buildServer.web.openapi.WebControllerManager
import org.assertj.core.api.Assertions
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.stream.Collectors
import javax.servlet.ServletOutputStream
import javax.servlet.WriteListener
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class UpdateImageControllerTest {

    @MockK
    private lateinit var server: SBuildServer

    @MockK
    private lateinit var webControllerManager: WebControllerManager

    @MockK
    private lateinit var updateImageProcessor: UpdateImageProcessor

    @InjectMockKs
    private lateinit var updateImageController: UpdateImageController

    @BeforeMethod
    fun setUp() = run {
        MockKAnnotations.init(this, relaxUnitFun = true)
        every { webControllerManager.registerController(any(), any()) } just Runs
    }

    @Test
    fun testWrongHttpVerb() {
        val req = mockk<HttpServletRequest>()
        every { req.method } returns "GET"
        val resp = mockk<HttpServletResponse>(relaxed = true)
        every { resp.setHeader(any(), any()) } just Runs
        val stream = TestServletOutputStream()

        every { resp.outputStream } returns stream

        updateImageController.handleRequest(req, resp)
        stream.flush()

        val res = stream.out.toString("UTF-8")

        Assertions.assertThat(res).contains("GET is not supported")
    }

    @Test
    fun testMissingProperties() {
        val req = mockk<HttpServletRequest>()
        every { req.method } returns "POST"
        every { req.parameterMap } returns emptyMap()

        val resp = mockk<HttpServletResponse>(relaxed = true)
        every { resp.setHeader(any(), any()) } just Runs
        val stream = TestServletOutputStream()

        every { resp.outputStream } returns stream

        updateImageController.handleRequest(req, resp)
        stream.flush()

        val res = stream.out.toString("UTF-8")

        val requiredProps = EnumSet.allOf(ImageUpdateProperties::class.java)
            .stream()
            .map { it.propertyName }
            .collect(Collectors.toList())


        Assertions.assertThat(res).contains("Missing required properties for the images data update: $requiredProps")
    }

    @Test
    fun runsNormally() {
        val req = mockk<HttpServletRequest>()
        every { req.method } returns "POST"
        val map = EnumSet.allOf(ImageUpdateProperties::class.java)
            .map { "prop:" + it.propertyName }
            .associateWith { arrayOf(UUID.randomUUID().toString()) }
        every { req.parameterMap } returns map
        every { req.getParameter(any()) } returns "prop:someProp"

        val resp = mockk<HttpServletResponse>(relaxed = true)
        every { resp.setHeader(any(), any()) } just Runs
        every { updateImageProcessor.processImageUpdate(any()) } returns UpdateImageResult("encrypted_val")
        val stream = TestServletOutputStream()

        every { resp.outputStream } returns stream

        updateImageController.handleRequest(req, resp)
        stream.flush()

        val res = stream.out.toString("UTF-8")

        Assertions.assertThat(res).contains("<passwords_data>")
    }

    @Test
    fun exceptionInServerLogic() {
        val req = mockk<HttpServletRequest>()
        every { req.method } returns "POST"
        val map = EnumSet.allOf(ImageUpdateProperties::class.java)
            .map { "prop:" + it.propertyName }
            .associateWith { arrayOf(UUID.randomUUID().toString()) }
        every { req.parameterMap } returns map
        every { req.getParameter(any()) } returns "prop:someProp"

        val resp = mockk<HttpServletResponse>(relaxed = true)
        every { resp.setHeader(any(), any()) } just Runs
        every { updateImageProcessor.processImageUpdate(any()) } throws RuntimeException()
        val stream = TestServletOutputStream()

        every { resp.outputStream } returns stream

        updateImageController.handleRequest(req, resp)
        stream.flush()

        val res = stream.out.toString("UTF-8")

        Assertions.assertThat(res).contains("Unexpected exception during the images data update")
    }
}

class TestServletOutputStream : ServletOutputStream() {

    val out = ByteArrayOutputStream()

    override fun write(b: Int) {
        out.write(b)
    }

    override fun isReady(): Boolean {
        return true
    }

    override fun setWriteListener(p0: WriteListener?) {
    }

}
