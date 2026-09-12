// Copyright 2026 The Jahaziel Authors
// SPDX-License-Identifier: Apache-2.0

package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"strconv"
	"strings"
	"syscall"
)

// Agent is a vsock connection to the node agent.
type Agent struct {
	conn net.Conn
	out  *json.Encoder
	in   *bufio.Scanner
}

// Dial connects to the host over vsock.
func Dial(hostCID, port uint32) (*Agent, error) {
	fd, err := syscall.Socket(syscall.AF_VSOCK, syscall.SOCK_STREAM, 0)
	if err != nil {
		return nil, err
	}
	addr := &syscall.SockaddrVM{CID: hostCID, Port: port}
	if err := syscall.Connect(fd, addr); err != nil {
		syscall.Close(fd)
		return nil, err
	}
	file := os.NewFile(uintptr(fd), "vsock")
	conn, err := net.FileConn(file)
	if err != nil {
		syscall.Close(fd)
		return nil, err
	}
	return &Agent{conn: conn, out: json.NewEncoder(conn), in: bufio.NewScanner(conn)}, nil
}

// Close closes the connection.
func (a *Agent) Close() error {
	return a.conn.Close()
}

// envelope is the host protocol frame.
type envelope struct {
	Type    string         `json:"type"`
	Payload map[string]any `json:"payload"`
}

func (a *Agent) send(msgType string, payload map[string]any) error {
	return a.out.Encode(envelope{Type: msgType, Payload: payload})
}

// Metrics is one resource sample.
type Metrics struct {
	MemTotalKB uint64 `json:"mem_total_kb"`
	MemAvailKB uint64 `json:"mem_avail_kb"`
	CPUUser    uint64 `json:"cpu_user_jiffies"`
	CPUSystem  uint64 `json:"cpu_system_jiffies"`
	Procs      uint64 `json:"procs"`
}

// ReportMetrics samples /proc and sends the sample.
func (a *Agent) ReportMetrics() error {
	metrics, err := Sample()
	if err != nil {
		return err
	}
	return a.send("metrics", map[string]any{
		"mem_total_kb":    metrics.MemTotalKB,
		"mem_avail_kb":    metrics.MemAvailKB,
		"cpu_user":        metrics.CPUUser,
		"cpu_system":      metrics.CPUSystem,
		"procs":           metrics.Procs,
	})
}

// ReportPort announces a newly bound TCP port.
func (a *Agent) ReportPort(port uint16) error {
	return a.send("port", map[string]any{"port": port, "protocol": "tcp"})
}

// Sample reads meminfo, stat, and the process count.
func Sample() (Metrics, error) {
	var metrics Metrics
	meminfo, err := os.ReadFile("/proc/meminfo")
	if err != nil {
		return metrics, err
	}
	for _, line := range strings.Split(string(meminfo), "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		value, err := strconv.ParseUint(fields[1], 10, 64)
		if err != nil {
			continue
		}
		switch fields[0] {
		case "MemTotal:":
			metrics.MemTotalKB = value
		case "MemAvailable:":
			metrics.MemAvailKB = value
		}
	}
	stat, err := os.ReadFile("/proc/stat")
	if err != nil {
		return metrics, err
	}
	for _, line := range strings.Split(string(stat), "\n") {
		fields := strings.Fields(line)
		if len(fields) == 0 {
			continue
		}
		switch fields[0] {
		case "cpu":
			if len(fields) > 4 {
				metrics.CPUUser, _ = strconv.ParseUint(fields[1], 10, 64)
				metrics.CPUSystem, _ = strconv.ParseUint(fields[3], 10, 64)
			}
		case "processes":
			if len(fields) > 1 {
				metrics.Procs, _ = strconv.ParseUint(fields[1], 10, 64)
			}
		}
	}
	return metrics, nil
}

// ListeningPorts returns locally bound TCP ports from /proc/net/tcp.
func ListeningPorts() ([]uint16, error) {
	data, err := os.ReadFile("/proc/net/tcp")
	if err != nil {
		return nil, err
	}
	var ports []uint16
	for i, line := range strings.Split(string(data), "\n") {
		if i == 0 {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 4 || fields[3] != "0A" {
			continue
		}
		addr := strings.Split(fields[1], ":")
		if len(addr) != 2 {
			continue
		}
		raw, err := strconv.ParseUint(addr[1], 16, 16)
		if err != nil {
			continue
		}
		ports = append(ports, uint16(raw))
	}
	return ports, nil
}

// Handle obeys one lifecycle command line from the host.
func Handle(line string) error {
	switch strings.TrimSpace(line) {
	case "flush":
		return nil
	case "shutdown":
		return fmt.Errorf("host requested shutdown")
	default:
		return fmt.Errorf("unknown command: %q", line)
	}
}
