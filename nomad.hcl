job "driver" {
  datacenters = ["us-west-2"]
  type = "service"

  update {
    stagger      = "30s"
    max_parallel = 1
  }

  group "driver" {
    count = 1

    task "driver" {
      driver = "docker"

      config {
        image = "483510943701.dkr.ecr.us-west-2.amazonaws.com/kleiner/driver:latest"
        network_mode = "host"
      }

      service {
        name = "driver"
        tags = ["urlprefix-driver.kleiner.ml/",
                "urlprefix-driver.service.consul:9999/"]
        port = "http"

        check {
          type     = "http"
          path     = "/health"
          interval = "10s"
          timeout  = "2s"
        }
      }

      env {
        DRIVER_THREADPOOL_SIZE = "1000"
        TARGET_HOST = "router.service.consul"
        TARGET_PORT = "9999"
      }

      resources {
        cpu    = 1000 # MHz
        memory = 2048 # MB

        network {
          mbits = 10
          port "http" {}
        }
      }
    }
  }
}
