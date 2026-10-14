# Security policy

## Supported versions

Prompt Critical is in pre-alpha and has no releases yet. Security fixes go to
`main`; once releases exist, the latest release line will be supported.

## Reporting a vulnerability

Please do not open a public issue for a vulnerability. Report it privately through
[GitHub's private vulnerability reporting](https://github.com/denfry/Prompt_Critical/security/advisories/new).

Include what you found, how to reproduce it, and what an attacker could do with it.
You can expect a first reply within 7 days. Once a fix is ready, the advisory is
published with credit to you unless you ask otherwise.

## Scope

In scope: anything in this repository that can be abused on a server or a client,
for example:

- network packets that let a client change server state it should not;
- CC: Tweaked peripherals and Lua APIs exposed by the mod;
- resource exhaustion that lets one player stall a server through the simulation;
- the build and release pipeline.

Out of scope: bugs in Minecraft, NeoForge, Create or CC: Tweaked themselves
(report those upstream), and in-game "exploits" that only break balance.
