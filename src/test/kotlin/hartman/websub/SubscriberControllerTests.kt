package hartman.websub

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest
class SubscriberControllerTests(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `Given a Subscriber When it POSTs a valid Subscription Request Then Hub should return Accepted`() {
        mockMvc.perform(
                post("/hub")
                        .characterEncoding( "utf-8")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("hub.callback", "http://localhost/subscribers/foo/callback")
                        .param("hub.mode", "subscribe")
                        .param("hub.topic", "http://localhost/topics/foo"))
                .andDo(print())
                .andExpect(status().isAccepted)
    }
}