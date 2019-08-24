package hartman.websub

import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.Parameter.param
import org.mockserver.verify.VerificationTimes.exactly
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.TimeUnit.SECONDS


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
class WebSubHubIntegrationTests(@Autowired val mockMvc: MockMvc) {

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

        MockServerClient("localhost", 1080)
                .verify(request()
                        .withMethod("GET")
                        .withPath("/a-fun-new-subscription")
                        .withQueryStringParameters(
                                param("hub.mode", "subscribe"),
                                param("hub.topic", "http://localhost/topics/foo")
                        ), exactly(1))
    }

    @Test
    fun `Given a Subscriber when a POST contains an unsupported mode then Hub should return 400 Bad Request`() {
        mockMvc.perform(
                post("/hub")
                        .characterEncoding("utf-8")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("hub.callback", "http://localhost/subscribers/foo/callback")
                        .param("hub.mode", "foobar")
                        .param("hub.topic", "http://localhost/topics/foo"))
                .andExpect(status().isBadRequest)
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string("Unsupported value for hub.mode: foobar"))
    }

    @Test
    fun `Given a Publisher when a topic is updated then Hub should POST to callback URL of Subscriber`() {

        MockServerClient("localhost", 1080)
                .`when`(
                        request()
                                .withMethod("GET")
                                .withPath("/barChanged")
                                .withQueryStringParameter("hub.challenge")
                        , Times.once())
                .respond { request ->
                    HttpResponse()
                            .withStatusCode(200)
                            .withBody(request.getFirstQueryStringParameter("hub.challenge"))
                }

        MockServerClient("localhost", 1080)
                .`when`(
                        request()
                                .withMethod("GET")
                                .withPath("/topics/bar")
                )
                .respond(
                        response("Come with me if you want to live!")
                                .withHeader("Content-Type", "text/plain; charset=UTF-8")
                )

        MockServerClient("localhost", 1080)
                .`when`(
                        request()
                                .withMethod("POST")
                                .withPath("/barChanged")
                        , Times.once())
                .respond(response())

        mockMvc.perform(
                post("/hub")
                        .characterEncoding("utf-8")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("hub.callback", "http://localhost:1080/barChanged")
                        .param("hub.mode", "subscribe")
                        .param("hub.topic", "http://localhost:1080/topics/bar"))

        mockMvc.perform(
                post("/hub")
                        .characterEncoding("utf-8")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("hub.mode", "publish")
                        .param("hub.topic", "http://localhost:1080/topics/bar"))
                .andExpect(status().isOk)

        await.atMost(2, SECONDS) untilAsserted {
            MockServerClient("localhost", 1080)
                    .verify(
                            request()
                                    .withMethod("GET")
                                    .withPath("/barChanged"),
                            request()
                                    .withMethod("GET")
                                    .withPath("/topics/bar"),
                            request()
                                    .withMethod("POST")
                                    .withPath("/barChanged")
                    )
        }
    }
}