package ai.sonora.mobile.data

/** JSON shaped by api/openapi.json 0.1.20. */
object Fixtures {
    const val OUTPUTS = """[
      {"outputId":"living","displayName":"Living Room","volume":70,"muted":false,"available":true,"enabled":true},
      {"outputId":"kitchen","displayName":"Kitchen","volume":55,"muted":true,"available":true,"enabled":true},
      {"outputId":"patio","displayName":"Patio","volume":10,"muted":false,"available":false,"enabled":false}
    ]"""

    const val GROUPS = """[
      {"groupId":"downstairs","displayName":"Downstairs","outputIds":["living","kitchen"],"muted":false,"enabled":true}
    ]"""

    const val ROUTES = """[
      {"routeId":"r1","inputId":"radio","targetId":"downstairs","targetType":"OUTPUT_GROUP","status":"ACTIVE",
       "transferable":true,"pauseable":false,"paused":false},
      {"routeId":"r2","inputId":"playlist","targetId":"patio","targetType":"SINGLE_OUTPUT","status":"STARTING",
       "pauseable":true,"paused":true}
    ]"""

    const val INPUTS = """[
      {"inputId":"radio","displayName":"Radio Paradise","uri":"http://radio.example/stream","enabled":true,
       "source":"STATIC","pauseable":false},
      {"inputId":"playlist","displayName":"Morning playlist","uri":"file:///music/morning.m3u","enabled":true,
       "source":"EPHEMERAL","pauseable":true}
    ]"""

    const val MASTER_MUTE = """{"muted":true}"""

    const val PROBLEM_NOT_FOUND =
        """{"type":"urn:multiroom:error:not-found","title":"Not Found","status":404,"detail":"No such thing"}"""
}
