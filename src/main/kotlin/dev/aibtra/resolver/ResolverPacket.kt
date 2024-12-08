package dev.aibtra.resolver

interface ResolverPacket {
	val idToStepToDebugDetails: Map<ResolverId, Map<String, String>>
}