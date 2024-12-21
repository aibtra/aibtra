package dev.aibtra.main.content

import java.io.*

fun interface RequestManagerFailureHandler {
	fun process(failure: IOException, mightBeAuthentication: Boolean)
}