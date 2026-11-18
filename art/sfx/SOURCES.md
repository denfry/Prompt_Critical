# Происхождение звуков машин

Все записи — **CC0 1.0** (Creative Commons Zero, общественное достояние): лицензия проверена на странице каждого
звука на Freesound 18.11.2026. Берётся превью высокого качества (`-hq.mp3`), хэш — в `sources.json`; если файл на
сервере изменится, `generate.py` остановится и лицензию надо проверить заново. Исходники в репозиторий не кладутся
(кэш — `art/sfx/.cache`, git-ignored); в мод идёт только обработанный отрезок `sounds/sfx/<key>.ogg`.

CC0 не требует указания авторства, но авторы перечислены — из уважения и для проверки.

| Звук | Автор | Запись | Отрезок, с | Петля | Обработка |
|---|---|---|---|---|---|
| `sfx.steam_engine_chuff` | birdOfTheNorth | [single steam train chuff](https://freesound.org/people/birdOfTheNorth/sounds/623817/) | 0.0–0.6 | нет | `highpass=f=60` |
| `sfx.boiler_fire` | SyntaxSeed | [Gas Torch Burn](https://freesound.org/people/SyntaxSeed/sounds/244230/) | 1.0–9.0 | да | `lowpass=f=900,highpass=f=40` |
| `sfx.cupola_blast` | qubodup | [Fire Flame Burner](https://freesound.org/people/qubodup/sounds/176724/) | 1.5–6.0 | да | `lowpass=f=2500,highpass=f=60` |
| `sfx.mine_fan` | IanStarGem | [Big Factory Fan Ambience](https://freesound.org/people/IanStarGem/sounds/272265/) | 2.0–11.0 | да | `highpass=f=30` |
| `sfx.pump` | unfa | [Industrial Water Machine Noise with Switch-Off](https://freesound.org/people/unfa/sounds/221551/) | 10.0–18.0 | да | `highpass=f=40` |
| `sfx.shearer_cutting` | felix.blume | [Jackhammer, pneumatic drill in underground mine](https://freesound.org/people/felix.blume/sounds/639019/) | 10.0–18.0 | да | `lowpass=f=3500,highpass=f=50,equalizer=f=150:t=q:w=1:g=6` |
| `sfx.hoist_winch` | Micronin | [electric hoist](https://freesound.org/people/Micronin/sounds/184888/) | 20.0–28.0 | да | `highpass=f=40` |
| `sfx.hoist_brake` | rabbydaw | [air_brakes_loud_fade_out.wav](https://freesound.org/people/rabbydaw/sounds/504865/) | 0.0–2.5 | нет | `highpass=f=80,afade=t=out:st=1.8:d=0.7` |
| `sfx.lathe` | chungus43A | [Lathe motor sound](https://freesound.org/people/chungus43A/sounds/783835/) | 0.3–4.3 | да | `highpass=f=40` |
| `sfx.milling` | chungus43A | [Milling Machine cutting metal](https://freesound.org/people/chungus43A/sounds/783833/) | 0.3–4.3 | да | `highpass=f=40` |
| `sfx.shaper_stroke` | Daphne_in_Wonderland | [metal grind.wav](https://freesound.org/people/Daphne_in_Wonderland/sounds/127151/) | 0.0–0.5 | нет | `lowpass=f=6000,afade=t=out:st=0.3:d=0.2` |
| `sfx.grinder` | Kinoton | [Angle grinder on/off grinding](https://freesound.org/people/Kinoton/sounds/347446/) | 8.0–12.0 | да | `highpass=f=80` |
| `sfx.metal_pour` | 1urker | [leidenfrost.ogg](https://freesound.org/people/1urker/sounds/523110/) | 52.5–57.5 | да | `lowpass=f=9000,highpass=f=120,acompressor=threshold=-36dB:ratio=8:attack=0.5:release=40:makeup=6` |
| `sfx.shakeout_rattle` | RutgerMuller | [Sounds For Earthquakes - Radiator Metal Rumbling](https://freesound.org/people/RutgerMuller/sounds/51097/) | 0.0–5.5 | да | `highpass=f=40` |
| `sfx.molding_jolt` | opriema | [thud_me.wav](https://freesound.org/people/opriema/sounds/400188/) | 0.15–0.95 | нет | `lowpass=f=3000,afade=t=out:st=0.55:d=0.25` |
| `sfx.breaker_operate` | sribubba | [Circuit Breaker 3 DP.wav](https://freesound.org/people/sribubba/sounds/130154/) | 0.0–0.42 | нет | `highpass=f=60,afade=t=out:st=0.35:d=0.07` |
| `sfx.relay_click` | JoontheFloof | [Relay 2](https://freesound.org/people/JoontheFloof/sounds/740252/) | 0.0–0.25 | нет | `highpass=f=200,afade=t=out:st=0.2:d=0.05` |
| `sfx.roof_support` | nuckan | [hydraulics.wav](https://freesound.org/people/nuckan/sounds/212941/) | 0.3–3.7 | нет | `highpass=f=60,afade=t=in:d=0.02,afade=t=out:st=2.8:d=0.6` |

## Синтез (art/sfx/generate.py, RECIPES)

| Звук | Как сделан |
|---|---|
| `sfx.agz_siren` | прямоугольная волна 660/880 Гц, смена тона каждые 0,25 с, эхо выработки |
| `sfx.electric_motor` | гул сети: 50 Гц, магнитострикция 100 Гц и гармоники 200/300 Гц, зубцовый тон 1250 Гц, шум вентилятора |
| `sfx.transformer_hum` | магнитострикция 100 Гц с гармониками 200/300/400 Гц, слабый шум |
| `sfx.geiger_click` | разряд счётчика: спад белого шума за 0,4 мс и звон корпуса 3,2 кГц, 30 мс, срез ниже 500 Гц |

Обработка всех файлов: моно, 44,1 кГц, постоянное усиление до RMS −18 dBFS при пике не выше −1 dBFS (без следящей
нормализации, чтобы громкость конца петли совпадала с началом), Vorbis q5. Петля — отрезок длиннее на 0,5 с, хвост
накладывается на нарастающее начало (`acrossfade`-приём через `afade` + `amix`), шов проверяется в `--check`.
