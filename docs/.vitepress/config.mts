// Copyright 2026 The Jahaziel Authors
// SPDX-License-Identifier: Apache-2.0
import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Jahaziel',
  description: 'Self-hosted browser development platform. One jar, port 11451.',
  themeConfig: {
    nav: [
      { text: 'Guide', link: '/guide/quick-start' },
      { text: 'Architecture', link: '/architecture/overview' },
      { text: 'Operations', link: '/operations/acceptance' },
      { text: 'Reference', link: '/reference/cli' },
    ],
    sidebar: {
      '/guide/': [
        { text: 'Quick start', link: '/guide/quick-start' },
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
      '/operations/': [
        { text: 'Backup', link: '/operations/backup' },
        { text: 'Reset', link: '/operations/reset' },
        { text: 'Verification', link: '/operations/verification' },
        { text: 'Acceptance', link: '/operations/acceptance' },
      ],
      '/reference/': [
        { text: 'CLI', link: '/reference/cli' },
        { text: 'Env vars', link: '/reference/env-vars' },
        { text: 'Ports', link: '/reference/ports' },
      ],
    },
    search: { provider: 'local' },
  },
})
