package hartman.websub

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
class Subscriber(
        var callbackUrl: String,
        var topicUrl: String
): AbstractEntity<Long>() {
    override fun toString() = "${super.toString()}, callbackUrl: $callbackUrl, topicUrl: $topicUrl"
}

@Repository
interface SubscriberRepository: CrudRepository<Subscriber, Long> {
    fun findAllByTopicUrl(topicUrl: String): Iterable<Subscriber>
}