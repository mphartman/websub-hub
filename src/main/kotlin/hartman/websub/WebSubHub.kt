package hartman.websub

import com.github.kittinunf.fuel.Fuel
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/")
class RootController {

    @GetMapping
    fun index(): String {
        return "Welcome to my WebSub Hub."
    }
}

@RestController
@RequestMapping("hub")
class WebSubSubController {

    @GetMapping
    fun index(): String {
        return "Subscribers POST here"
    }

    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun subscribe(@RequestParam subscriptionRequest: Map<String, String>): ResponseEntity<Any> {
        println("Received subscription request $subscriptionRequest")
        val callback = subscriptionRequest["hub.callback"].orEmpty()
        val mode = subscriptionRequest["hub.mode"].orEmpty()
        val topicUrl = subscriptionRequest["hub.topic"].orEmpty()
        verifySubscriberIntent(callback, mode, topicUrl)
        return ResponseEntity.accepted().build()
    }

    fun verifySubscriberIntent(callback: String, mode: String, topicUrl: String) {
        val challenge = generateNewChallenge()
        println("($callback, $topicUrl) - Verifying subscriber intent with GET, challenge = $challenge")
        Fuel.get(callback, listOf("hub.mode" to mode, "hub.topic" to topicUrl, "hub.challenge" to challenge, "hub.lease_seconds" to "0"))
                .response { request, response, result ->
                    println("($callback, $topicUrl) - Response from Subscriber verification")
                    println(request)
                    println(response)
                    val (bytes, error) = result
                    if (bytes != null) {
                        val body = String(bytes)
                        if (challenge == body) {
                            println("Challenge from Subscriber matches!")
                        }
                        else {
                            println("Challenge from Subscriber does NOT match")
                        }
                    }
                }
    }

    fun generateNewChallenge() : String {
        return java.util.UUID.randomUUID().toString()
    }
}
