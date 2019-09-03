package hartman.websub

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.result.Result
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.accepted
import org.springframework.http.ResponseEntity.badRequest
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val HUB_URL = "http://websubhub.us-east-1.elasticbeanstalk.com/hub"

data class SubscriberKey(val callbackUrl: String, val topicUrl: String)

@RestController
@RequestMapping("hub")
class WebSubSubController(@Autowired val subscriberRepository: SubscriberRepository) {

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
                        val subscriber = SubscriberKey(callback, topic)
                        when (mode) {
                            "subscribe" -> {
                                verifySubscriberIntent(subscriber, mode) {
                                    createOrUpdateSubscriber(it.callbackUrl, it.topicUrl, body["hub.secret"], body["hub.lease_seconds"])
                                }
                            }
                            "unsubscribe" -> {
                                verifySubscriberIntent(subscriber, mode) {
                                    deleteSubscriber(it.callbackUrl, it.topicUrl)
                                }
                            }
                        }
                        log.info("Accepted $mode request from $subscriber")
                        accepted().build()
                    }
                }
            }
            "publish" -> {
                val topic = body["hub.topic"]
                val url = body["hub.url"]
                if (topic != null) notifySubscribersOfTopicUpdate(topic, url)
                ok().build()
            }
            else -> badRequest().body("Unsupported value for hub.mode: $mode")
        }
    }

    private fun createOrUpdateSubscriber(callback: String, topic: String, secret: String?, leaseSecondsParam: String?): Subscriber {
        val leaseSeconds = leaseSecondsParam?.toLong() ?: 0
        val subscriber = subscriberRepository.findByCallbackUrlAndTopicUrl(callback, topic)
                .orElse(Subscriber(callback, topic, secret, leaseSeconds))
        subscriber.secret = secret
        subscriber.expires = if (leaseSeconds <= 0) 0 else System.currentTimeMillis() + (leaseSeconds * 1_000)
        return subscriberRepository.save(subscriber)
    }

    private fun deleteSubscriber(callback: String, topic: String) {
        subscriberRepository.findByCallbackUrlAndTopicUrl(callback, topic).ifPresent { subscriberRepository.delete(it) }
    }

    private fun verifySubscriberIntent(subscriber: SubscriberKey, mode: String, onSuccess: (SubscriberKey) -> Unit) {
        val challenge = generateNewChallenge()
        log.info("Verifying $subscriber intent of $mode with GET using challenge = $challenge")
        Fuel.get(subscriber.callbackUrl, listOf("hub.mode" to mode, "hub.topic" to subscriber.topicUrl, "hub.challenge" to challenge, "hub.lease_seconds" to "0"))
                .response { _, response, result ->
                    log.info("${response.statusCode} status code from Subscriber confirmation response")
                    if (response.statusCode == 200) {
                        log.info("Checking challenge from $subscriber verification response")
                        val (bytes, error) = result
                        if (bytes != null) {
                            val body = String(bytes)
                            if (challenge == body) {
                                log.info("Challenge from $subscriber response matches! Subscriber verified. Action will be carried out.")
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

    private fun notifySubscriberDenied(subscriber: SubscriberKey, reason: String) {
        Fuel.get(subscriber.callbackUrl, listOf("hub.mode" to "denied", "hub.topic" to subscriber.topicUrl, "hub.reason" to reason))
                .response()
    }

    private fun notifySubscribersOfTopicUpdate(topicUrl: String, resourceUrl: String?) {
        val topicResourceUrl = resourceUrl ?: topicUrl
        log.info("GET resource from $topicResourceUrl")
        Fuel.get(topicResourceUrl).response { _, response, result ->
            when (result) {
                is Result.Failure -> {
                    log.info("Failed to GET resource at $topicResourceUrl. Subscribers will not be notified")
                }
                is Result.Success -> {
                    var contentType = "text/plain; charset=UTF-8"
                    val headerValues = response.headers[Headers.CONTENT_TYPE]
                    if (headerValues.isNotEmpty()) {
                        contentType = headerValues.first()
                    }
                    subscriberRepository.findAllByTopicUrl(topicUrl)
                            .filter { it.expires == 0L || it.expires > System.currentTimeMillis() }
                            .forEach { notifySubscriber(it, contentType, result.get()) }
                }
            }
        }
    }

    private fun notifySubscriber(subscriber: Subscriber, contentType: String, content: ByteArray) {
        log.info("POST to $subscriber with $contentType")
        val req = Fuel.post(subscriber.callbackUrl)
                .header(Headers.CONTENT_TYPE to contentType)
                .header("Link", "<${HUB_URL}>; rel=\"hub\"", "<${subscriber.topicUrl}>; rel=\"self\"")
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
