LISA backend microservice

[Build Status](https://build.tax.service.gov.uk/job/LISA/job/lisa/)

| Repositories      | Link                                           |
|-------------------|------------------------------------------------|
| Frontend          | https://github.com/hmrc/lisa-frontend          |
| Stub              | https://github.com/hmrc/lisa-stub              |
| API tests         | https://github.com/hmrc/lisa-api-tests         |
| Performance tests | https://github.com/hmrc/lisa-performance-tests |

## Requirements

This service is written in [Scala 3](http://www.scala-lang.org/) and [Play 3.0](http://playframework.com/), and needs at least Java 11 to run.

## Running Locally

You will need to have the following:

The service manager profile for this service is `LISA`

1. **[Install Service-Manager](https://github.com/hmrc/sm2)**
2. `git clone git@github.com:hmrc/lisa.git`
3. `sbt "run 8886"`
4. `sm2 --start LISA_FRONTEND_ALL`

The unit tests can be run by running
```
sbt test
```

To run a single unit test
```
sbt testOnly SPEC_NAME
```

## Testing the Service

This service uses [sbt-scoverage](https://github.com/scoverage/sbt-scoverage) to provide test coverage reports.

Run this script before raising a PR to ensure your code changes pass the Jenkins pipeline. This runs all the unit tests  and checks for dependency updates:

```
./run_all_tests.sh
```

## Resources

| Method | URL                                            | Description |
| :---: | ----------------------------------------------- | ---------------------------------------------------------------------- |
|  GET  |  /tax-enrolments/groups/:groupId/subscriptions  | TaxEnrolmentController.getSubscriptionsForGroupId(groupId: String) |
|  POST |  /:utr/register                                 | ROSMController.register(utr: String) |
|  POST |  /:utr/subscribe/:lisaManagerRef                | ROSMController.submitSubscription(utr: String, lisaManagerRef: String) |
|  GET  |  /rosm/callback                                 | ROSMController.subscriptionCallback() |
|  POST |  /rosm/callback                                 | ROSMController.subscriptionCallback() |
|  PUT  |  /rosm/callback                                 | ROSMController.subscriptionCallback() |

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
