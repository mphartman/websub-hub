package hartman.websub

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/")
class RootController {

    @GetMapping
    fun index(): String {
        return "Welcome to my WebSub Hub."
    }
}

@RestController
@RequestMapping("hub")
class WebSubSubController {

    @PostMapping(consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun subscribe(@RequestParam subscriptionRequest: Map<String, String>): ResponseEntity<Any> {
        print(subscriptionRequest)
        return ResponseEntity.accepted().build()
    }

}
