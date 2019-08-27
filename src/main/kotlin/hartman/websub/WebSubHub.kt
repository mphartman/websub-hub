package hartman.websub

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.result.Result
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.accepted
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.notFound
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RestController
@RequestMapping("hub")
class WebSubSubController(@Autowired val subscriberRepository: SubscriberRepository) {
    private val HUB_URL = "http://websubhub.us-east-1.elasticbeanstalk.com/hub"

    private val log = LoggerFactory.getLogger(WebSubSubController::class.java)

    @GetMapping
    fun index(): String {
        return "Hub"
    }

    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun handleHubRequest(@RequestParam body: Map<String, String>): ResponseEntity<Any> {
        log.info("Received Hub request $body")
        return when (val mode = body["hub.mode"]) {
            "subscribe", "unsubscribe" -> {
                val callback = body["hub.callback"]
                val topic = body["hub.topic"]
                return when {
                    callback == null -> badRequest().body("Missing required parameter value for hub.callback")
                    topic == null -> badRequest().body("Missing required parameter value for hub.topic")
                    else -> {
                        return when (mode) {
                            "subscribe" -> {
                                val secret = body["hub.secret"]
                                val subscriber = findOrCreateSubscriber(callback, topic, secret)
                                verifySubscriberIntentAsync(subscriber, mode) {
                                    // do nothing
                                }
                                log.info("Accepted $mode request from $subscriber")
                                accepted().build()
                            }
                            "unsubscribe" -> {
                                val subscriber = subscriberRepository.findByCallbackUrlAndTopicUrl(callback, topic)
                                return if (subscriber.isPresent) {
                                    verifySubscriberIntentAsync(subscriber.get(), mode) {
                                        subscriberRepository.delete(it)
                                        log.info("Deleted $it")
                                    }
                                    log.info("Accepted $mode request from $subscriber")
                                    accepted().build()
                                } else {
                                    log.info("Not Found for $mode request from $subscriber")
                                    notFound().build()
                                }
                            }
                            else -> badRequest().build()
                        }
                    }
                }
            }
            "publish" -> {
                val topic = body["hub.topic"]
                if (topic != null) notifySubscribersOfTopicUpdate(topic)
                ok().build()
            }
            else -> badRequest().body("Unsupported value for hub.mode: $mode")
        }
    }

    private fun findOrCreateSubscriber(callback: String, topic: String, secret: String?): Subscriber {
        return subscriberRepository.findByCallbackUrlAndTopicUrl(callback, topic).orElseGet {
            subscriberRepository.save(Subscriber(callback, topic, secret))
        }
    }

    private fun verifySubscriberIntentAsync(subscriber: Subscriber, mode: String, onSuccess: (Subscriber) -> Unit) {
        GlobalScope.launch {
            val challenge = generateNewChallenge()
            log.info("Verifying $subscriber intent of $mode with GET using challenge = $challenge")
            Fuel.get(subscriber.callbackUrl, listOf("hub.mode" to mode, "hub.topic" to subscriber.topicUrl, "hub.challenge" to challenge, "hub.lease_seconds" to "0"))
                    .response { result ->
                        log.info("Checking challenge from $subscriber verification response")
                        val (bytes, error) = result
                        if (bytes != null) {
                            val body = String(bytes)
                            if (challenge == body) {
                                log.info("Challenge from $subscriber response matches! Subscriber verified.")
                                onSuccess(subscriber)
                            } else {
                                log.info("Challenge response [$body] from $subscriber does NOT match. Subscriber denied.")
                                notifySubscriberDenied(subscriber, "challenge")
                            }
                        }
                    }
        }
    }

    private fun generateNewChallenge() = java.util.UUID.randomUUID().toString()

    private fun notifySubscriberDenied(subscriber: Subscriber, reason: String) {
        Fuel.get(subscriber.callbackUrl, listOf("hub.mode" to "denied", "hub.topic" to subscriber.topicUrl, "hub.reason" to reason))
                .response()
    }

    private fun notifySubscribersOfTopicUpdate(topicUrl: String) {
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

    private fun notifySubscriber(subscriber: Subscriber, contentType: String, content: ByteArray) {
        log.info("POST to $subscriber with $contentType")
        val req = Fuel.post(subscriber.callbackUrl)
                .header(Headers.CONTENT_TYPE to contentType)
                .header("Link", "<$HUB_URL>; rel=\"hub\"", "<${subscriber.topicUrl}>; rel=\"self\"")
                .body(content)

        if (subscriber.secret != null) {
            val key = subscriber.secret!!.toByteArray(charset("UTF8"))
            req.appendHeader("X-Hub-Signature", "sha256=${hex(hmacSHA256(key, content))}")
        }

        req.response { _ -> }
    }

    private fun hmacSHA256(key: ByteArray, data: ByteArray): ByteArray {
        val algorithm = "HmacSHA256"
        return Mac.getInstance(algorithm).run {
            init(SecretKeySpec(key, algorithm))
            doFinal(data)
        }
    }

    private fun hex(data: ByteArray): String = data.fold(StringBuilder()) { acc, next ->
        acc.append(String.format("%02x", next))
    }.toString().toLowerCase()

}
