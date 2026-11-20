# Plant world: setting it up on another machine

The `Plant` world is not in the repository (it lives in `pc-mod/run-film/saves`, 50 MB): it is rebuilt from seed 62
and the generator, block for block. This folder keeps what the rebuild needs:

| File | What |
|---|---|
| `plant_site.json` | terrain heights and biomes around the plant on seed 62 (`/film heightmap`) |
| `gorge_site.json`, `gorge_north.json`, `line_site.json` | the gorge and the power line corridor, used by the older `write2` layout |
| `level.dat` | template world settings for `newworld.py` (game rules, datapacks) |

## Steps

1. Put the film client's extra mods into `pc-mod/run-film/mods/` (all from Modrinth, NeoForge 1.21.1):
   `mcpfabric-neoforge-0.10.0+1.21.1`, `Jade-1.21.1-NeoForge-15.10.6`, `SereneSeasons-neoforge-1.21.1-10.1.0.9`,
   `GlitchCore-neoforge-1.21.1-2.1.0.2`. For the mcpfabric bridge also set its token and `enableUiControl` in
   `pc-mod/run-film/config/mcpfabric.config.json`.
2. Create the world and the command files:
   ```bash
   python art/film/newworld.py art/film/site pc-mod/run-film/saves/Plant 62 -200 760
   python art/film/plantworld.py pc-mod/run-film
   ```
3. Start the client in it: `bash art/film/launch.sh Plant` (returns when the player is in the world).
4. In game: `/film run plant` (builds everything, a few minutes on a fresh world), then `/film run plant_start`
   (forceload, start-up of every unit with the wind turbines one by one, and `/film feed` to keep them running).
   After any later restart only `/film run plant_start` is needed.
5. Shots: `python art/film/shots_plant.py pc-mod/run-film/film.json` (add `--stills` for a storyboard), then start
   the client again: FilmRecorder records `film.json` on entering the world.
