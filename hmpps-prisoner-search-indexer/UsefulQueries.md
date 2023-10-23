# Useful App Insights Queries

#### General logs (filtering out the offender update)
``` kusto
traces
| where cloud_RoleName == "prisoner-offender-search"
| where message !startswith "Updating offender"
| order by timestamp desc
```

#### General logs including spring startup
``` kusto
traces
| where cloud_RoleInstance startswith "prisoner-offender-search"
| order by timestamp desc
```

#### Interesting exceptions
``` kusto
exceptions
| where cloud_RoleName == "prisoner-offender-search"
| where operation_Name != "GET /health"
| where customDimensions !contains "health"
| where details !contains "HealthCheck"
| order by timestamp desc
```

#### Indexing requests
``` kusto
requests
| where cloud_RoleName == "prisoner-offender-search"
//| where timestamp between (todatetime("2020-08-06T18:20:00") .. todatetime("2020-08-06T18:22:00"))
| order by timestamp desc
```

#### Prison API requests during index build
``` kusto
requests
| where cloud_RoleName == "prison-api"
| where name == "GET OffenderResourceImpl/getOffenderNumbers"
| where customDimensions.clientId == "prisoner-offender-search-client"
```

```kusto
requests
| where cloud_RoleName == "prison-api"
| where name == "GET OffenderResourceImpl/getOffender"
| where customDimensions.clientId == "prisoner-offender-search-client"
| order by timestamp desc
```
