package hartman.websub

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager

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
}