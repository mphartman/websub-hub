package hartman.websub

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/")
class RootController {

    @GetMapping
    fun index(): String {
        return "Welcome to my WebSub Hub."
    }
}
