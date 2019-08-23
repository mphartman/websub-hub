package hartman.websub

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.Parameter.param
import org.mockserver.verify.VerificationTimes.exactly
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status


@WebMvcTest
class SubscriberControllerTests(@Autowired val mockMvc: MockMvc) {

    private lateinit var mockServer: ClientAndServer

    @BeforeAll
    fun startServer() {
        mockServer = ClientAndServer.startClientAndServer(1080)
    }

    @AfterAll
    fun stopServer() {
        mockServer.stop()
    }

    @Test
    fun `Given a Subscriber when it POSTs a valid Subscription Request then Hub should return Accepted`() {
        mockMvc.perform(
                post("/hub")
                        .characterEncoding("utf-8")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("hub.callback", "http://localhost/subscribers/foo/callback")
                        .param("hub.mode", "subscribe")
                        .param("hub.topic", "http://localhost/topics/foo"))
                .andExpect(status().isAccepted)
    }

    @Test
    fun `Given a Subscriber when it POSTs a valid Subscription Request then Hub should verify intent of Subscriber`() {
        mockMvc.perform(
                post("/hub")
                        .characterEncoding("utf-8")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("hub.callback", "http://localhost:1080/a-fun-new-subscription")
                        .param("hub.mode", "subscribe")
                        .param("hub.topic", "http://localhost/topics/foo"))
                .andReturn()

        MockServerClient("localhost", 1080)
                .verify(request()
                        .withMethod("GET")
                        .withPath("/a-fun-new-subscription")
                        .withQueryStringParameters(
                                param("hub.mode", "subscribe"),
                                param("hub.topic", "http://localhost/topics/foo")
                        ), exactly(1))
    }
}