package hartman.websub

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import javax.persistence.PersistenceException

@DataJpaTest
class SubscriberTests @Autowired constructor(
        val testEntityManager: TestEntityManager,
        val subscriberRepository: SubscriberRepository) {

    @Test
    fun `When findAllByTopicUrl then return Subscribers`() {
        val sub1 = testEntityManager.persist(Subscriber("http://sub1", "http://topic"));
        val sub2 = testEntityManager.persist(Subscriber("http://sub2", "http://topic"));
        testEntityManager.flush()

        val subscribers = subscriberRepository.findAllByTopicUrl(topicUrl = "http://topic")

        assertThat(subscribers).containsExactlyInAnyOrder(sub1, sub2)
    }

    @Test
    fun `When findByCallbackUrlAndTopicUrl then return Subscriber`() {
        val sub1 = testEntityManager.persist(Subscriber("http://sub1", "http://topic"));
        val sub2 = testEntityManager.persist(Subscriber("http://sub2", "http://topic"));
        val sub3 = testEntityManager.persist(Subscriber("http://sub3", "http://topic1"));
        testEntityManager.flush()

        val maybeSubscriber = subscriberRepository.findByCallbackUrlAndTopicUrl(callbackUrl = "http://sub2", topicUrl = "http://topic")

        assertThat(maybeSubscriber).isPresent.get().isEqualTo(sub2)
    }

    @Test
    fun `callbackUrl and topicUrl must be unique`() {
        assertThatThrownBy {
            val sub1 = testEntityManager.persist(Subscriber("http://sub1", "http://topic"));
            val sub2 = testEntityManager.persist(Subscriber("http://sub1", "http://topic"));
            testEntityManager.flush()
        }.isInstanceOf(PersistenceException::class.java)
    }
}