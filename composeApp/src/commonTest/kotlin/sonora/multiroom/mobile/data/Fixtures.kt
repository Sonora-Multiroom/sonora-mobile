package sonora.multiroom.mobile.data

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

    /**
     * An extensions inventory shaped like the 0.1.21 contract: five extensions covering every status
     * and connection state. The hub is not reachable from cloud sessions, so this is written from
     * the schema, not copied from a live answer.
     */
    const val EXTENSIONS = """{"loadingEnabled":true,"extensionsDirectory":"/opt/multiroom/extensions","extensions":[
      {"id":"tts","name":"Text to speech","version":"1.2.0","requiredApiVersion":"0.1.0","status":"ACTIVE","connectionState":"NOT_APPLICABLE"},
      {"id":"dlna","name":"DLNA renderer","version":"0.4.1","requiredApiVersion":"0.1.0","status":"ACTIVE","connectionState":"CONNECTED"},
      {"id":"mqtt","name":"MQTT bridge","version":"0.2.0","requiredApiVersion":"0.1.0","status":"ACTIVE","connectionState":"DISCONNECTED"},
      {"id":"legacy","name":"Legacy plugin","version":"0.0.9","requiredApiVersion":"9.9.9","status":"REJECTED","connectionState":"NOT_APPLICABLE","rejectionReason":"Requires API 9.9.9"},
      {"id":"spare","name":"Spare","version":"1.0.0","requiredApiVersion":"0.1.0","status":"INERT","connectionState":"NOT_APPLICABLE"}
    ]}"""

    const val MASTER_MUTE = """{"muted":true}"""

    const val PROBLEM_NOT_FOUND =
        """{"type":"urn:multiroom:error:not-found","title":"Not Found","status":404,"detail":"No such thing"}"""
}
