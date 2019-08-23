package hartman.websub

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WebSubHubApplication

fun main(args: Array<String>) {
    runApplication<WebSubHubApplication>(*args)
}