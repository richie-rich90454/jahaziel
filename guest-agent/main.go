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
	"time"
)

func main() {
	host := flag.Uint("host-cid", 2, "host vsock CID")
	port := flag.Uint("port", 9001, "host vsock port")
	interval := flag.Duration("interval", 10*time.Second, "metrics interval")
	flag.Parse()

	agent, err := Dial(uint32(*host), uint32(*port))
	if err != nil {
		log.Fatalf("vsock dial: %v", err)
	}
	defer agent.Close()

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
