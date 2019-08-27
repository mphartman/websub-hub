package hartman.websub

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import javax.persistence.Entity
import javax.persistence.Table
import javax.persistence.UniqueConstraint

@Table(uniqueConstraints = [
    UniqueConstraint(columnNames = ["callbackUrl", "topicUrl"])
])
@Entity
class Subscriber(
        var callbackUrl: String,
        var topicUrl: String,
        var secret: String? = null
) : AbstractEntity<Long>() {
    override fun toString() = "${super.toString()}, callbackUrl: $callbackUrl, topicUrl: $topicUrl"
}

@Repository
interface SubscriberRepository : CrudRepository<Subscriber, Long> {
    fun findAllByTopicUrl(topicUrl: String): Iterable<Subscriber>
    fun findByCallbackUrlAndTopicUrl(callbackUrl: String, topicUrl: String): Optional<Subscriber>
}