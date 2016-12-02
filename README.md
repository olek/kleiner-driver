# kleiner-driver
Backend to generate fake cases and send them to router, along with API to control it.

## Useage

Run it with lein

    lein run

Build it as deployable jar which can be found in `target/kleiner-driver-0.1.0-standalone.jar`

    lein clean && lein build

Run that jar file

    ./entrypoint-dev.sh


## Using with Docker

Build docker image

    docker build -t kleiner/driver .

Run it with docker

    ./docker-boot-dev.sh

## Environment Variables
| ENV Variable            | Description                                      | Default Value    |
|-------------------------|--------------------------------------------------|------------------|
| THREADPOOL_SIZE         | Thread pool size for 'workers' that pull target  | 10               |
| TARGET_HTTP_METHOD      | Currently 'http' is the only value supported     | http             |
| TARGET_HOST             | Host for target endpoint                         | localhost        |
| TARGET_PORT             | Port for target endpoint                         | 8080             |
| TARGET_PATH             | Path for target endpoint                         | /prediction/stub |

## Sample generated request

    {"org": 1, "text": "foo", "case": 123, "prediction_type": "sentiment"}

## Sample of expected response

    {"score": 123.0}


## Sample Requests
Local server runs on port 8080, docker dev script is configured to map to local port 8080 as well.
```
$ curl -s 'http://olek.desk.local:8080/stats' | underscore print
{
  "1": {
    "sent-cases": { "rate": 3.7, "count": 43 },
    "predictions": { "rate": 3.6, "count": 42 },
    "errors": { "rate": 0, "count": 0 },
    "timeouts": { "rate": 0, "count": 0 },
    "target-rate": 10
  }
}

$ curl -v 'http://olek.desk.local:8080/pulse' -d rate=1 -d duration=2
...
< HTTP/1.1 204 No Content
...

$ curl -v 'http://olek.desk.local:8080/reset' -X POST
...
< HTTP/1.1 204 No Content
...

$ curl -v 'http://olek.desk.local:8080/set-target-rate' -d org=1 -d rate=0
...
< HTTP/1.1 204 No Content
...

$ curl -v 'http://olek.desk.local:8080/set-target-rate-percentage' -d org=1 -d rate=0
...
< HTTP/1.1 204 No Content
...

```
