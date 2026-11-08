// Copyright 2026 The Jahaziel Authors
// SPDX-License-Identifier: Apache-2.0
import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Jahaziel',
  description: 'A lightweight self-hosted browser IDE for trusted users working on Linux servers.',
  themeConfig: {
    nav: [
      { text: 'Guide', link: '/guide/quick-start' },
      { text: 'Deploy', link: '/deployment/single-server' },
      { text: 'Architecture', link: '/architecture/overview' },
      { text: 'Operations', link: '/operations/acceptance' },
      { text: 'Reference', link: '/reference/cli' },
    ],
    sidebar: {
      '/': [
        { text: 'About Jahaziel', link: '/' },
        { text: 'Plan', link: '/plan' },
        { text: 'Naming', link: '/naming' },
      ],
      '/guide/': [
        { text: 'Quick start', link: '/guide/quick-start' },
        { text: 'Using Jahaziel', link: '/guide/using-jahaziel' },
        { text: 'Configuration', link: '/guide/configuration' },
        { text: 'Platforms', link: '/guide/platforms' },
        { text: 'Demo mode', link: '/guide/demo-mode' },
        { text: 'Templates', link: '/guide/templates' },
        { text: 'Share links', link: '/guide/share-links' },
      ],
      '/architecture/': [
        { text: 'Overview', link: '/architecture/overview' },
        { text: 'Platform', link: '/architecture/platform' },
        { text: 'IDE', link: '/architecture/ide' },
        { text: 'Workspaces', link: '/architecture/workspaces' },
      ],
      '/ide/': [
        {
          text: 'IDE history',
          items: [
            { text: 'Retired server build', link: '/ide/building-the-ide-server' },
            { text: 'Historical patch manifest', link: '/ide/patch-manifest' },
            { text: 'Historical telemetry audit', link: '/ide/telemetry-audit' },
          ],
        },
      ],
      '/deployment/': [
        { text: 'Linux server deployment', link: '/deployment/single-server' },
      ],
      '/operations/': [
        { text: 'Overview', link: '/operations/' },
        { text: 'Backup', link: '/operations/backup' },
        { text: 'Reset', link: '/operations/reset' },
        { text: 'Verification', link: '/operations/verification' },
        { text: 'Acceptance', link: '/operations/acceptance' },
        { text: 'Restore drill log', link: '/operations/restore-drill-log' },
      ],
      '/reference/': [
        { text: 'CLI', link: '/reference/cli' },
        { text: 'Env vars', link: '/reference/env-vars' },
        { text: 'Ports', link: '/reference/ports' },
        { text: 'Error codes', link: '/reference/errors' },
      ],
      '/security/': [
        { text: 'Threat model', link: '/security/threat-model' },
      ],
      '/contributing/': [
        { text: 'Docs contract', link: '/contributing/docs-contract' },
        {
          text: 'Java for application code',
          link: '/contributing/architecture-decisions/java-for-application-code',
        },
        {
          text: 'No external messaging services',
          link: '/contributing/architecture-decisions/no-external-messaging-services',
        },
        {
          text: 'Lightweight browser workbench',
          link: '/contributing/architecture-decisions/lightweight-workbench',
        },
        {
          text: 'Historical: VS Code OSS server base',
          link: '/contributing/architecture-decisions/ide-server-base',
        },
      ],
    },
    search: { provider: 'local' },
  },
})
