package org.niis.example.restapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RestApiApplication

fun main(args: Array<String>) {
	runApplication<RestApiApplication>(*args)
}
