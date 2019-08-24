package hartman.websub

import org.springframework.data.util.ProxyUtils
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.MappedSuperclass
import java.io.Serializable

@MappedSuperclass
abstract class AbstractEntity<T : Serializable> {

    companion object {
        private val serialVersionUID = -5554308939380869754L
    }

    @Id
    @GeneratedValue
    private var id: T? = null

    fun getId(): T? {
        return id
    }

    override fun equals(other: Any?): Boolean {
        other ?: return false

        if (this === other) return true

        if (javaClass != ProxyUtils.getUserClass(other)) return false

        other as AbstractEntity<*>

        return if (null == this.getId()) false else this.getId() == other.getId()
    }

    override fun hashCode(): Int {
        return 31
    }

    override fun toString() = "${this.javaClass.name} id: $id"

}