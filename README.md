# websub-hub

A WebSub hub implementation written in Kotlin

## Features

* Ability for subscribers to subscribe and unsubscribe
* Subscriber intent validation and verification
* Subscription lease subscription expiration
* Content distribution from publishers to subscribers
* Authenticated content distribution for subscribers using a `hub.secret` with the subscription - supprts sha256 only

## Publishers

A publisher wishing to inform this hub of updates send a POST request to the hub URL with
`application/x-www-form-urlencoded` encoded parameters of:
* `hub.mode` set with value of "publish" 
* `hub.topic` set with the URL of the resource that should be sent to subscribers.

## References

* WebSub - W3C Recommendation 23 January 2018 - https://www.w3.org/TR/websub/
* WebSub implementation validator - https://websub.rocks/

