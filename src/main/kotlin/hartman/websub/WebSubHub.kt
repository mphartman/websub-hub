package hartman.websub

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.result.Result
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.*
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("hub")
class WebSubSubController(@Autowired val subscriberRepository: SubscriberRepository) {

    private val log = LoggerFactory.getLogger(WebSubSubController::class.java)

    @GetMapping
    fun index(): String {
        return "Hub"
    }

    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun subscribe(@RequestParam body: Map<String, String>): ResponseEntity<Any> {
        log.info("Received Hub request $body")
        return when (val mode = body["hub.mode"]) {
            "subscribe" -> {
                val callback = body["hub.callback"]
                val topic = body["hub.topic"]
                return when {
                    callback == null -> badRequest().body("Missing required parameter value for hub.callback")
                    topic == null -> badRequest().body("Missing required parameter value for hub.topic")
                    else -> verifySubscriberIntent(callback, mode, topic)
                }
            }
            "publish" -> {
                val topic = body["hub.topic"].orEmpty()
                notifySubscribersOfTopicUpdate(topic)
                ok().build()
            }
            else -> badRequest().body("Unsupported value for hub.mode: $mode")
        }
    }

    fun verifySubscriberIntent(callback: String, mode: String, topic: String): ResponseEntity<Any> {
        val subscriber = Subscriber(callback, topic)
        val challenge = generateNewChallenge()
        log.info("Verifying $subscriber intent with GET using challenge = $challenge")
        Fuel.get(callback, listOf("hub.mode" to mode, "hub.topic" to topic, "hub.challenge" to challenge, "hub.lease_seconds" to "0"))
                .response { result ->
                    log.info("Checking challenge from $subscriber verification response")
                    val (bytes, error) = result
                    if (bytes != null) {
                        val body = String(bytes)
                        if (challenge == body) {
                            log.info("Challenge from $subscriber response matches! Subscriber verified.")
                            val newSubscriber = subscriberRepository.save(subscriber)
                            log.info("Saved $newSubscriber")
                        } else {
                            log.info("Challenge response [$body] from $subscriber does NOT match. Subscriber denied.")
                        }
                    }
                }
        return accepted().build()
    }

    fun generateNewChallenge() = java.util.UUID.randomUUID().toString()

    fun notifySubscribersOfTopicUpdate(topicUrl: String) {
        log.info("GET resource from $topicUrl")
        Fuel.get(topicUrl).response { _, response, result ->
            when (result) {
                is Result.Failure -> {
                    log.info("Failed to GET resource at $topicUrl. Subscribers will not be notified")
                }
                is Result.Success -> {
                    var contentType = "text/plain; charset=UTF-8"
                    val headerValues = response.headers[Headers.CONTENT_TYPE]
                    if (headerValues.isNotEmpty()) {
                        contentType = headerValues.first()
                    }
                    subscriberRepository.findAllByTopicUrl(topicUrl).forEach { notifySubscriber(it, contentType, result.get()) }
                }
            }
        }
    }

    fun notifySubscriber(subscriber: Subscriber, contentType: String, content: ByteArray) {
        log.info("POST to $subscriber with $contentType")
        Fuel.post(subscriber.callbackUrl)
                .header(Headers.CONTENT_TYPE to contentType)
                .header("Link", "<http://websubhub.us-east-1.elasticbeanstalk.com/hub>; rel=\"hub\"", "<${subscriber.topicUrl}>; rel=\"self\"")
                .body(content)
                .response { _ -> }
    }
}
