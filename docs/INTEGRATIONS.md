# Integrations Guide

This app uses `CrowdRepository` to abstract data sources. Replace `MockRepository` with a real `SensorRepository` that implements the same interface.

## Data contracts
- ZoneRisk: `zoneId`, `displayName`, `risk (0..1)`, `minutesToCritical`
- HeatCell: grid cell `x,y`, `intensity (0..1)`
- Playbook: id, title, steps, isActive
- TaskItem: id, title, assignee, location, status
- Incident: id, message, severity (Info/Amber/Red)
- Kpi: label, value

## Example: CCTV/Video pipeline
- Edge model (e.g., YOLO/ByteTrack + crowd density map)
- Publish short-horizon risk per zone and heat grid

Pseudocode:
```kotlin
class SensorRepository(/* deps */) : CrowdRepository {
    private val _zoneRisks = MutableStateFlow<List<ZoneRisk>>(emptyList())
    override val zoneRisks = _zoneRisks.asStateFlow()
    // ... other flows

    override suspend fun start() {
        coroutineScope {
            launch { pollVideoEngine() }
            launch { pollSchedules() }
            // ...
        }
    }

    private suspend fun pollVideoEngine() {
        videoClient.stream().collect { frame ->
            val zones = riskFromDetections(frame.detections)
            _zoneRisks.value = zones
            _heat.value = heatFromDensity(frame.heat)
        }
    }
}
```

Payload example from edge box:
```json
{
  "timestamp": 1712345678,
  "zones": [
    {"id":"zA","name":"Zone A","risk":0.68,"ttc":4},
    {"id":"zB","name":"Zone B","risk":0.42,"ttc":6}
  ],
  "heat": {"cols":10,"rows":5,"values":[0.1,0.2,...]}
}
```

## Example: BLE/Wi‑Fi probe counts
- Aggregator emits counts by zone per 15–30s; convert to risk deltas.

```kotlin
countsFlow.collect { counts ->
    val updated = mergeCountsIntoRisks(_zoneRisks.value, counts)
    _zoneRisks.value = updated
}
```

## Example: LiDAR/Thermal chokepoints
- Provide compression index; if > threshold, raise amber/red incident and increase risk for affected zone.

## Playbooks -> Actuation
- On `activatePlaybook(id)`, call integrations:
  - VMS/PA controllers
  - SMS/WhatsApp broadcast (geo-targeted)
  - Workforce app/task updates

## Reliability
- Use `stateIn` + cached last-good values
- Exponential backoff on network failure
- Store-and-forward for offline edge nodes

## Security/Privacy
- No PII; anonymized counts
- TLS for all channels; signed payloads
- Role-based access for control actions

