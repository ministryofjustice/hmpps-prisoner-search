# Custom Alerts

#### Synthetic Monitor

There is a Cronjob called `synthetic-monitor` which performs a simple prisoner search every 10 minutes. It then records the number of results and the request duration as telemetry events on Application Insights.

You can see those telemetry events over time with these App Insights log queries:

```kusto
customEvents
| where cloud_RoleName == "prisoner-offender-search"
| where name == "synthetic-monitor"
| extend timems=toint(customDimensions.timeMs),results=toint(customDimensions.results)
| summarize avg(timems),max(timems) by bin(timestamp, 15m)
| render timechart
```

```kusto
customEvents
| where cloud_RoleName == "prisoner-offender-search"
| where name == "synthetic-monitor"
| extend timems=toint(customDimensions.timeMs),results=toint(customDimensions.results)
| summarize avg(results),max(results) by bin(timestamp, 15m)
| render timechart
```

An alert has been created for each metric in Application Insights.

* `Prisoner Offender Search - response time (synthetic monitor)` - checks if the average response time for the search is higher than an arbitrary limit. This indicates that the system is performing slowly and you should investigate the load on the system.
* `Prisoner Offender Search - result size (synthetic monitor` - checks if the number of results returned by the search has dropped below an arbitrary limit. This indicates that either the data in the system has drastically changed or that there is some kind of bug with the search meaning not all results are being found.
