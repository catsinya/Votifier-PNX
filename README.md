# Votifier-PNX

Minimal Votifier receiver for PowerNukkitX, inspired by the vote handling in Zombies.

## What it does

- Listens for Votifier v2 vote packets on a configurable port
- Stores votes for offline players in `votes.yml`
- Delivers pending votes when the player joins
- Exposes a `/vote` command that shows the configured vote links
- Runs configurable console reward commands when a vote is received

## Configuration

Edit `src/main/resources/config.yml` before building, or change the generated `config.yml` in the plugin data folder after first start.

### `reward-commands`

Use `%player%` and `%service%` placeholders in reward commands.

Example:

```yml
vote-links:
  - name: MinePortal
    url: https://mineportal.jp/servers/cmmbvjbdq00009jgcxecbqdco
reward-commands:
  - give %player% diamond 1
  - say %player% voted on %service%
```
