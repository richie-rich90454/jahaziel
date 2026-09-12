<!--

    Copyright 2026 The Jahaziel Authors
    SPDX-License-Identifier: Apache-2.0

-->

# IDE patch manifest

One coherent change per patch, applied in order by `ide/build.sh`. Patches are
rebased onto each new upstream stable tag. Security-relevant patches need two
approvals.

| Patch | Change | Verification |
|---|---|---|
| 0001 | Rebrand `product.json` | Branding smoke |
| 0002 | Rebrand runtime strings (generated) | Brand-string grep |
| 0003 | Replace icons and fonts | Asset smoke |
| 0004 | Remove all telemetry | No-network test |
| 0005 | Remove AI features | Patch regression |
| 0006 | Remove Live Share | Patch regression |
| 0007 | Remove remote extensions | Patch regression |
| 0008 | Open VSX marketplace | Install smoke |
| 0009 | OIDC session auth | Session-auth smoke |
| 0010 | Disable autoupdate | No-network test |
| 0011 | Disable Microsoft settings sync | No-network test |
| 0012 | Server runtime config | Server boot smoke |
| 0013 | Brand theme tokens | Brand smoke |
| 0014 | Browser compatibility | Browser matrix |
