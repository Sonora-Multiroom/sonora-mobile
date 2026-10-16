// Hub calls for Maestro flows (runScript). HUB comes from `maestro test -e HUB=...`, the rest from
// the step's env. A thrown error fails the step, so an expectation that does not hold stops the flow.
//
//   ACTION=stopAll
//   ACTION=start    INPUT TARGET TYPE(SINGLE_OUTPUT|OUTPUT_GROUP)   -> output.routeId
//   ACTION=play     URI TARGET TYPE                                 -> output.routeId (a link)
//   ACTION=stop     ROUTE
//   ACTION=pause    ROUTE PAUSED(true|false)
//   ACTION=enable   KIND(outputs|groups|inputs) ID ENABLED(true|false)
//   ACTION=addInput ID NAME URI
//   ACTION=removeInput ID                  (no error if it is gone)
//   ACTION=expectEnabled KIND ID ENABLED
//   ACTION=expectRoutes  COUNT [INPUT]       number of routes (of INPUT, if given)
//   ACTION=expectOutputs ROUTE OUTPUTS       comma-separated outputs the route plays on, in any order
//   ACTION=expectNoInput ID
//   ACTION=extensions                       -> output.extFirst, output.extLast (names, A to Z)

const api = HUB.replace(/\/$/, '') + '/api/v2'

function call(method, path, body) {
    const res = http.request(api + path, {
        method: method,
        headers: { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
    })
    if (res.status >= 300 && !(method === 'DELETE' && res.status === 404)) {
        throw new Error(method + ' ' + path + ' answered ' + res.status + ': ' + res.body)
    }
    return res.body ? json(res.body) : null
}

function routes() {
    return call('GET', '/routes')
}

function find(kind, id) {
    const key = { outputs: 'outputId', groups: 'groupId', inputs: 'inputId' }[kind]
    return call('GET', '/' + kind + '?includeDisabled=true').filter(function (x) { return x[key] === id })[0]
}

function expect(ok, message) {
    if (!ok) throw new Error('expected ' + message)
}

if (ACTION === 'stopAll') {
    routes().forEach(function (r) { call('DELETE', '/routes/' + r.routeId) })
} else if (ACTION === 'start') {
    const r = call('POST', '/routes', { inputId: INPUT, targetId: TARGET, targetType: TYPE })
    output.routeId = r.routeId
} else if (ACTION === 'play') {
    const r = call('POST', '/play', { uri: URI, targetId: TARGET, targetType: TYPE })
    output.routeId = r.routeId
} else if (ACTION === 'stop') {
    call('DELETE', '/routes/' + ROUTE)
} else if (ACTION === 'pause') {
    call('PUT', '/routes/' + ROUTE + '/pause', { paused: PAUSED === 'true' })
} else if (ACTION === 'enable') {
    call('PUT', '/' + KIND + '/' + ID + '/enabled', { enabled: ENABLED === 'true' })
} else if (ACTION === 'addInput') {
    call('POST', '/inputs', { inputId: ID, displayName: NAME, uri: URI, autoRemove: false })
} else if (ACTION === 'removeInput') {
    if (find('inputs', ID)) call('DELETE', '/inputs/' + ID)
} else if (ACTION === 'expectEnabled') {
    const x = find(KIND, ID)
    expect(x && String(x.enabled) === ENABLED, KIND + '/' + ID + ' enabled=' + ENABLED + ', was ' + (x && x.enabled))
} else if (ACTION === 'expectRoutes') {
    const rs = routes().filter(function (r) { return typeof INPUT === 'undefined' || r.inputId === INPUT })
    expect(rs.length === Number(COUNT), COUNT + ' routes' + (typeof INPUT === 'undefined' ? '' : ' of ' + INPUT) + ', found ' + rs.length)
} else if (ACTION === 'expectOutputs') {
    const r = routes().filter(function (x) { return x.routeId === ROUTE })[0]
    const want = OUTPUTS.split(',').sort().join(',')
    const got = r ? (r.outputs || []).slice().sort().join(',') : 'no route'
    expect(got === want, 'route ' + ROUTE + ' on ' + want + ', was on ' + got)
} else if (ACTION === 'expectNoInput') {
    expect(!find('inputs', ID), 'no input ' + ID)
} else if (ACTION === 'extensions') {
    const names = call('GET', '/extensions').extensions.map(function (e) { return e.name }).sort()
    output.extFirst = names[0]
    output.extLast = names[names.length - 1]
} else {
    throw new Error('unknown ACTION ' + ACTION)
}
