# kleiner-driver
Backend to generate fake cases and send them to router, along with API to control it.

## Usage

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
| API_PORT                | Port for driver's API server                     | 8080             |
| API_THREADPOOL_SIZE     | Thread pool size for API server http 'workers'   | 15               |
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
$ curl -s 'http://localhost:8080/stats' | underscore print
{
  "1": {
    "sent-cases": { "rate": 3.7, "count": 43 },
    "predictions": { "rate": 3.6, "count": 42 },
    "skips": { "rate": 0, "count": 0 },
    "errors": { "rate": 0, "count": 0 },
    "timeouts": { "rate": 0, "count": 0 },
    "target-rate": 10
  }
}

$ curl -s 'http://localhost:8080/recent' | underscore print
{
  "1": [
    [{ "org": 1, "text": "foo", "case": 1774312, "prediction_type": "sentiment" }, 42],
    [{ "org": 1, "text": "foo", "case": 1773608, "prediction_type": "sentiment" }, 42],
    [{ "org": 1, "text": "foo", "case": 1773864, "prediction_type": "sentiment" }, 42],
    [{ "org": 1, "text": "foo", "case": 1774536, "prediction_type": "sentiment" }, 42],
    [{ "org": 1, "text": "foo", "case": 1774859, "prediction_type": "sentiment" }, 42]
  ]
}

$ curl -v 'http://localhost:8080/pulse' -d rate=1 -d duration=2
...
< HTTP/1.1 204 No Content
...

$ curl -v 'http://localhost:8080/reset' -X POST
...
< HTTP/1.1 204 No Content
...

$ curl -v 'http://localhost:8080/set-target-rate' -d org=1 -d rate=0
...
< HTTP/1.1 204 No Content
...

$ curl -v 'http://localhost:8080/set-target-rate-percentage' -d org=1 -d rate=0
...
< HTTP/1.1 204 No Content
...

```

## Driving the entire system locally

Check out all Kleiner repos (including kleiner-driver) under some directory.

If you are using docker for mac increase the memory from the default 2GB to, say, 5GB.

    cd kleiner-driver/
    ./scripts/kleiner-bootstrap.sh

This will pull the latest for each repo, then build the docker images and run them.

You can now run the UI or hit the driver directly via the api. Check that everything is fine by hitting stats:

    curl -s 'http://localhost:8080/stats'

Warm up the JVMs by running

    curl -v 'http://localhost:8080/pulse' -d rate=100 -d duration=20

Check the stats again (see above) and then reset the stats

    curl -v 'http://localhost:8080/reset' -X POST

You are now ready to drive the system. If you are trying to benchmark you can try

    curl -v 'http://localhost:8080/pulse' -d rate=1000 -d duration=20

and hit the stats endpoint throughout the run. You want to see an actual rate of around 1000 cases per minute.

Also make sure you are seeing a total of rate*duration predictions at the end of the run.
