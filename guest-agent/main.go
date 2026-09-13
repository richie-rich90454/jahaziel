// Copyright 2026 The Jahaziel Authors
// SPDX-License-Identifier: Apache-2.0

// Guest agent: reports metrics and port binds to the node agent over vsock
// and obeys lifecycle commands. Runs as jahaziel-guest, never listens on
// the network. Shell command audit flows through the terminal stream, not
// here; process-level tracing arrives with the kernel helper.
package main

import (
	"flag"
	"log"
	"os"
	"time"
)

func main() {
	host := flag.Uint("host-cid", 2, "host vsock CID")
	port := flag.Uint("port", 9001, "host vsock port")
	interval := flag.Duration("interval", 10*time.Second, "metrics interval")
	flag.Parse()

	var agent *Agent
	for {
		var err error
		agent, err = Dial(uint32(*host), uint32(*port))
		if err == nil {
			break
		}
		log.Printf("vsock dial: %v; retrying", err)
		time.Sleep(10 * time.Second)
	}
	defer agent.Close()

	go obey(agent)

	ticker := time.NewTicker(*interval)
	defer ticker.Stop()

	seen := map[uint16]bool{}
	for {
		select {
		case <-ticker.C:
			if err := agent.ReportMetrics(); err != nil {
				log.Printf("metrics: %v", err)
			}
			bound, err := ListeningPorts()
			if err != nil {
				log.Printf("ports: %v", err)
				continue
			}
			for _, p := range bound {
				if !seen[p] {
					seen[p] = true
					if err := agent.ReportPort(p); err != nil {
						log.Printf("port %d: %v", p, err)
					}
				}
			}
		}
	}
}

// obey dispatches host lifecycle commands until shutdown or disconnect.
func obey(agent *Agent) {
	for agent.Scan() {
		if err := Handle(agent.Text()); err != nil {
			if err == errShutdown {
				log.Print("host requested shutdown")
				os.Exit(0)
			}
			log.Printf("host command: %v", err)
		}
	}
}
