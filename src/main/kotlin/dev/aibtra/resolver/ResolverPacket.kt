package dev.aibtra.resolver

import dev.aibtra.ai.*

interface ResolverPacket {
	val profileName: AIProfile.Name
	val supportsResolveOnly: Boolean
	val idToStepToDebugDetails: Map<ResolverId, Map<String, String>>
}