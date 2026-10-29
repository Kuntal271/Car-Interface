# Canbox frames — deep dive (draft 1, 2026-10-30)

Goal: know exactly which vehicle data can reach the head unit through the Canbox, in which frame, and what is missing — BEFORE designing screens.
Status legend: [V] verified on real captured bytes · [S] taken from open-source firmware/head-unit code · [?] unknown until we look at the real unit.

## 1. The data path (four layers, each can hide data from the next)

    EcoSport CAN bus (HS-CAN 500k)
        |  car-side CAN frames (rich: rpm, speed, boost, TPMS, gear, steering angle ...)   [S Racelogic DB]
        v
    CANBOX  (small decoder box on the harness; brand family = Raise "RZC" / Hiworld "WC" / Simple "XP" / OuDi "OD")
        |  UART, 38400 8N1, 3.3 V, ~20-60 bytes/s average            <- "the frames"   [S]
        v
    HEAD-UNIT MCU  (separate microcontroller; on some platforms it forwards frames raw to Android, on others it decodes them itself)  [?]
        |  vendor protocol
        v
    ANDROID vendor service  ("CarService"/"CanBus"/"Car Info" app)  ->  what the stock UI and OUR app can see      [?]

Our app lives on the last layer. Rooting Android does not reach the MCU. What we can read depends on what the vendor service re-exposes (broadcast / Binder / properties) — recon on the real unit decides. A UART tap at layer 2 gives ground truth regardless.

## 2. Wire formats

Raise (RZC), header 0x2E [V]:

    2E | type | len | data[len] | ck          ck = ((type + len + sum(data)) & 0xFF) ^ 0xFF

Four independent real frames reproduce this checksum exactly (2E 20 02 0A 02 D1 "hang up"; 2E 21 05 00 10 00 00 00 C9; 2E 22 04 01 01 01 01 D5; 2E 23 04 01 01 01 01 D4). Two other published examples did not match (a README illustration and a garbled forum dump) and are ignored.
Head unit acknowledges each canbox frame with a single 0xFF byte. At power-up the box emits a 0x00 then a burst of frames.

Hiworld (WC), header 0x5A 0xA5 [S, not yet verified on a capture]:

    5A A5 | len | type | data[len] | ck      ck = (len + type + sum(data) - 1) & 0xFF

Tool: tools/canbox_decode.py parses both with checksum resync (unit-tested), prints a decode per frame and a per-type census (count, distinct payloads, Hz).

## 3. Raise frame catalogue

Canbox -> head unit. "Ford" = RZC_FordSeriesProtocol/Manager (decompiled head-unit service from a Raise-partner Android unit); "VW" = smartgauges/canbox firmware + RZC_VolkswagenSeriesProtocol.

| type | meaning | payload (what is known) | dialect |
|------|---------|--------------------------|---------|
| 0x01 | panel key | ? | Ford |
| 0x14 | backlight level | d0 = level | Ford, VW |
| 0x16 | vehicle speed info | [?] layout | VW |
| 0x20 | steering-wheel key | d0 = key code, d1 = 0 release / 1 press / 2 hold | all |
| 0x21 | air conditioning | Ford: d0 b7 AC on, b6 A/C compressor, b5 recirc, b3 auto, b2 dual, b1 front defrost; d1 b4 show-bar, b7/b6/b5 face/mid/feet, b0-3 fan; d2/d3 L/R temp (0x1F..0x3B = 15.5C + 0.5C steps, 0x00 LO, 0x7F HI); d4 b6 unit F, b2 max-AC; d5 outside temp (signed) | Ford, VW |
| 0x22 | rear radar | 4 bytes, left..right | all |
| 0x23 | front radar | 4 bytes | all |
| 0x24 | base info | Ford: d0 b6 FL door, b7 FR, b4 RL, b5 RR, b3 trunk; VW-PQ: reverse / handbrake / lights bits; MQB: doors | all (differs) |
| 0x25 | park assist | Ford: d0 b2 front radar on, b3 rear on | Ford, VW |
| 0x26 | Ford: date-time (6 bytes) / VW: steering angle (-540..540) | | differs |
| 0x27 | Ford: language / VW: amplifier status | | differs |
| 0x29 | steering angle (MQB) | int16 | VW MQB |
| 0x30 | protocol version | ASCII string — this is the "CanBox version" shown in Car Info | all |
| 0x40 | SYNC version | | Ford |
| 0x50-0x53 | SYNC menu / item / play-time / call-time | UTF-16 menu text | Ford |
| 0x70-0x72, 0x78, 0x79 | SYNC SRT up/down/short, SYNC status, audio request | | Ford |
| 0x41 | vehicle info | VW sub 0x01 doors; sub 0x02: rpm(u16) speed*100(u16) volt*100(u16) outside-temp*10(u16) odometer(u24) low-fuel(u8); sub 0x03: low-fuel/low-volt flags | VW |

Head unit -> canbox: 0x81 start/stop (d0 = 1/0), 0x82 set time, 0x90 request a frame type (d0 = type), 0x91 car model select (Ford: 0x01 generic, 0x02 Kuga-17), 0xC6 SYNC control, 0xA0/0xA2/0xC0-0xC5 VW amplifier/navi/source/media.

### The important finding

Owner report (2026-10-30): the stock UI DOES show RPM, speed and fuel. So the unit receives them from somewhere; with a Canbox that points at the dialect or revision in use (Hiworld, or a Raise revision newer than the one decompiled here) rather than the Ford-Raise handler I read. Treat "which frame carries rpm/speed/fuel" as the first thing the capture must answer.

In the Ford dialect, the head-unit side declares properties for engine speed, speedometer, battery voltage, odometer and remaining fuel, but its frame handler has NO case that fills them: there is no 0x41 or 0x16 handler. The Ford dialect delivers doors, A/C (with outside temperature), radar, steering keys, backlight, time and SYNC emulation. RPM and speed come in the VW dialect (0x41 sub 02).
So if your head unit really shows RPM and speed, one of these is true: (a) your Canbox/dialect is not Raise-Ford (Hiworld? a newer Raise-Ford version?), (b) a newer protocol revision adds a vehicle-info frame, (c) speed is GPS-derived and RPM comes from somewhere else. This is exactly what the capture must settle.

## 4. Hiworld frame catalogue (partial, from Peugeot-407 and VW-MQB profiles)

0x00 heartbeat · 0x11 SWC (+ steering angle / park flags) · 0x12 doors/body · 0x22-0x24 radar · 0x26 steering angle · 0x31 climate · 0x33/0x34 trip/consumption/range · 0x39 TPMS · 0x41 radar · 0x42/0xEA alerts · 0x68/0x69/0x6A settings/config · 0x81/0x82 amplifier · 0x90/0xCA model discovery · 0x91/0x92 media text · 0xA6 clock sync. Dialects differ per car and per head-unit APK; treat names as hints. Palm Bay 1000 installs in India are commonly done with HiWorld CAN boxes (door status, A/C, steering keys, parking sensors shown) — so this unit certainly speaks Hiworld.

## 5. Signal matrix for YOUR car (what exists at each layer)

| signal | car CAN (Racelogic EcoSport 2013-) | Raise-Ford frame | Raise-VW 0x41/02 | Hiworld | OBD-II PID |
|--------|------|------|------|------|------|
| engine rpm | yes | none seen | yes | [?] | 01 0C |
| vehicle speed | yes (indicated) | none seen | yes | [?] | 01 0D |
| battery volts | no (not in list) | no | yes | [?] | 01 42 / ELM ATRV |
| fuel level | yes | no | low-fuel flag only | [?] | 01 2F |
| outside temp | no | yes (0x21 d5) | yes | [?] | 01 46 |
| odometer | yes | no | yes | [?] | no (Mode 09/22) |
| doors/trunk | not listed | yes (0x24) | yes | yes (0x12) | no |
| A/C state + set temp | no | yes (0x21) | yes | yes (0x31) | no |
| steering keys | no | yes (0x20) | yes | yes | no |
| steering angle | yes | no | yes (0x26/0x29) | yes (0x26) | no |
| parking radar | no | yes (0x22/0x23) | yes | yes | no |
| TPMS pressures | yes | no | no | yes (0x39, Peugeot dialect) | no |
| gear | yes | no | no | [?] | no |
| BOOST / MAP | yes (boost pressure) | no | no | no | 01 0B (+ 01 33 baro) |
| coolant temp | no (not listed) | no | no | no | 01 05 |
| intake temp / load / throttle | yes (IAT, pedal) | no | no | no | 01 0F / 04 / 11 |

Consequence: a Canbox can never give boost, coolant, load or throttle. For a turbo-car dashboard those need the OBD port (ELM327-class adapter, read-only, already designed in M1). The Canbox box taps the radio connector, so the OBD socket stays free, and a passive ELM poller coexists with it.

## 6. How we capture ground truth (pick in this order)

A. Look at the unit (zero risk, 5 min, no tools): Settings -> Car Info / CanBox: note the "CanBox version" string (= 0x30 payload; names the brand + protocol revision) and, in Factory settings -> CAN protocol, the brand (Raise RZC / Hiworld WC / Simple XP / OuDi OD) and car model selected. Watch which live values the stock Car Info screens show (rpm? speed? fuel? volts?).
B. Android-side tap (needs adb; root only if the vendor service is permission-gated): logcat for CarService/CanBus tags while driving; `dumpsys activity services`; look for broadcasts; check whether raw frames are visible to Android. Recon commands live in the plan (M4).
C. UART tap (best, independent of firmware): 3.3 V USB-UART adapter (CP2102/CH340/FT232, ~Rs 300) wired GND + RX only to the canbox TX wire (the line to the head unit; "H/Unit RX Can data, 3.3 V" on 20-pin boxes). Confirm voltage with a multimeter first (never connect a 5 V line to a 3.3 V adapter without a divider). Read-only: our tool never writes. Run `canbox_decode.py --port ... --proto raise|hiworld --log cap.jsonl` while: idling, revving, driving, A/C changes, doors, wipers/lights, SWC buttons, reverse. The census then lists every frame type the box emits for your car.
D. Vendor-service hook (root): only if B shows the data is MCU-decoded and unreachable.

Never transmit on the canbox UART or the car bus from our code (head-unit -> canbox frames can change vehicle settings on some cars).

## 7. Open items

1. Which canbox family/model/version is installed (A)?
2. Does any frame carry rpm/speed for this car (B/C)?
3. Is boost wanted enough to buy an OBD adapter (recommended: yes — it is the signature gauge of a 1.0 EcoBoost)?
4. SYNC-emulation frames (0x40-0x79) exist for Ford boxes — irrelevant now that SYNC is gone, but ignore them in the parser.

Sources: smartgauges/canbox (canbox.c), icarome/VwRaiseCanbox, darkspr1te/Work MctCoreServices RZC_Ford/VW protocol classes, fazerxlo/canbox-core docs, XDA "CANBOX CAN decoder reverse engineering", DUDU-AUTO canbox thread, Racelogic EcoSport 2013- CAN database, Car Tech Hub Palm Bay 1000 installs (HiWorld).
