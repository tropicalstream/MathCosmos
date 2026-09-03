#!/usr/bin/env python3
"""
Turn a written tour into the JSON the app plays.

A tour script is a few hundred spoken lines whose cue times, keyframes and segment offsets all
have to agree with each other and with how long the voices actually take to say them. Doing that
by hand is how Tour II of InnerCosmos ended up with two lines overrunning their followers, so
MathCosmos writes the prose and lets this compute the clock.

INPUT  — a "source" JSON, one per tour, that a human (or an agent) writes:

    {
      "id": 1,
      "title": "MathCosmos I: The Unknown",
      "hudTitle": "MATHCOSMOS I - THE UNKNOWN",
      "transitMs": 22000,                  # default travel time between stops
      "stops": [
        {
          "name": "THE BALANCE",
          "label": "1.  THE BALANCE",      # depth-menu row; defaults from name
          "transitMs": 26000,              # optional per-stop override (travel INTO the next stop)
          "lines": [
            {"role": "NAVIGATION", "view": 0, "sfx": "drive_engage",
             "text": "[warm] Helm to all stations..."},
            {"role": "SCIENCE", "view": 1, "text": "[gentle] ..."}
          ],
          "transitLines": [                # optional: spoken while under way to the next stop
            {"role": "ENGINEERING", "text": "[dry] ..."}
          ]
        }
      ],
      "fillers": [{"role": "ENGINEERING", "text": "[dry] ..."}]
    }

OUTPUT — assets/tourN_script.json, in the schema TourDirector.parse() reads.

Clip ids are the role, the first few content words, and a digest of the whole spoken line. Editing
one line changes only that line's id, so every other rendered voice file is left untouched — but
editing ANY part of a line does change its id, which is what stops a revised line from silently
keeping the audio of the sentence it replaced.

    python3 app/tools/build_script.py source/tour1.json app/src/main/assets/tour1_script.json
    python3 app/tools/build_script.py source/tour1.json app/src/main/assets/tour1_script.json --measure

--measure re-times the whole script from the voice clips that have actually been rendered
(ffprobe), instead of from the estimate. Run it after generating audio; it is what keeps a line
from being talked over by the one behind it.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
VOICE = HERE.parent / "src" / "main" / "assets" / "voice"
ROLE_FOLDER = {"NAVIGATION": "navigation", "SCIENCE": "science", "ENGINEERING": "engineering"}

# Fish s2.1-pro speaks at about 2.45 words a second in this register, and takes a beat at every
# sentence end. Measured against the rendered InnerCosmos clips; --measure replaces it with truth.
WORDS_PER_SEC = 2.45
SENTENCE_PAUSE_MS = 260
LEAD_IN_MS = 350
GAP_MS = 1400            # breath between two lines inside a stop
STOP_GAP_MS = 2200       # a slightly longer beat at a stop boundary
DEFAULT_TRANSIT_MS = 22000
TAIL_MS = 6000           # let the last line land before the tour ends


def strip_tags(text):
    """The spoken words only: acting tags like [warm] are direction for the voice, not speech."""
    return re.sub(r"\s+", " ", re.sub(r"\[[^\]]*\]", " ", text)).strip()


def estimate_ms(text):
    spoken = strip_tags(text)
    words = len(spoken.split())
    sentences = len(re.findall(r"[.!?…]", spoken)) or 1
    return int(LEAD_IN_MS + words / WORDS_PER_SEC * 1000 + sentences * SENTENCE_PAUSE_MS)


def measured_ms(clip, role):
    """Real duration of a rendered clip, or None if it has not been generated yet."""
    folder = VOICE / ROLE_FOLDER[role]
    for ext in ("ogg", "wav"):
        f = folder / f"{clip}.{ext}"
        if not f.exists():
            continue
        try:
            out = subprocess.run(
                ["ffprobe", "-v", "error", "-show_entries", "format=duration",
                 "-of", "default=noprint_wrappers=1:nokey=1", str(f)],
                capture_output=True, text=True, check=True).stdout.strip()
            return int(float(out) * 1000)
        except (subprocess.CalledProcessError, ValueError, FileNotFoundError):
            return None
    return None


def slug(text, words=4):
    spoken = strip_tags(text).lower()
    parts = re.findall(r"[a-z0-9]+", spoken)
    skip = {"the", "a", "an", "and", "of", "to", "is", "it", "that", "this", "we", "you", "i"}
    kept = [p for p in parts if p not in skip][:words]
    if not kept:
        kept = parts[:words] or ["line"]
    return "_".join(kept)


def clip_id(prefix, role, text, used):
    """
    A clip's name, derived from what it SAYS.

    The readable slug is the first few content words, which is what makes a voice directory
    browsable. On its own that is not enough: edit the back half of a line and the slug is
    unchanged, so the generator sees the old file, skips it, and the tour ships audio that says
    something different from its own caption. That is exactly what happened when the scripts were
    revised — thirty-eight clips carried the previous wording — and it is invisible until you put
    the glasses on and listen to a line disagree with the words under it.

    So the name carries a short digest of the whole spoken line. Any edit anywhere in the line
    changes the name, the old file is orphaned, and the new one is rendered. Unchanged lines keep
    their name and are skipped as before, so a re-render after a small edit is still cheap.
    """
    digest = hashlib.sha1(strip_tags(text).encode("utf-8")).hexdigest()[:6]
    base = f"{prefix}{role[:3].lower()}_{slug(text)}_{digest}"
    name = base
    n = 2
    while name in used:
        name = f"{base}_{n}"
        n += 1
    used.add(name)
    return name


def build(src, measure=False):
    tour_id = src["id"]
    prefix = src.get("clipPrefix", f"m{tour_id}_")
    stops = src["stops"]
    default_transit = src.get("transitMs", DEFAULT_TRANSIT_MS)

    used = set()
    cues = []
    keyframes = []
    segments = []
    warnings = []
    t = 0

    def emit(line, stop_index):
        nonlocal t
        cid = clip_id(prefix, line["role"], line["text"], used)
        dur = None
        if measure:
            dur = measured_ms(cid, line["role"])
            if dur is None:
                warnings.append(f"no rendered clip for {cid} - estimated")
        if dur is None:
            dur = estimate_ms(line["text"])
        cue = {"t": t, "role": line["role"], "clip": cid, "text": line["text"]}
        if "view" in line:
            cue["view"] = line["view"]
        if "sfx" in line:
            cue["sfx"] = line["sfx"]
        cues.append(cue)
        t += dur + line.get("gapMs", GAP_MS)
        return dur

    for i, stop in enumerate(stops):
        segments.append({
            "label": stop.get("label", f"{i + 1}.  {stop['name']}"),
            "startMs": max(0, t - 1500),   # open the segment just before its first line
            "node": i,
        })
        keyframes.append([t, i])
        for line in stop.get("lines", []):
            emit(line, i)
        t += STOP_GAP_MS - GAP_MS
        keyframes.append([t, i])           # hold at the stop for the whole of its block

        if i < len(stops) - 1:
            transit = stop.get("transitMs", default_transit)
            transit_lines = stop.get("transitLines", [])
            if transit_lines:
                start = t
                for line in transit_lines:
                    emit(line, i)
                spoken = t - start
                if spoken > transit:
                    warnings.append(
                        f"stop {i + 1} '{stop['name']}': transit talk runs {spoken // 1000}s but the "
                        f"transit is {transit // 1000}s - the transit was stretched to fit")
                    transit = spoken + 2000
                t = start + transit
            else:
                t += transit
            keyframes.append([t, i + 1])

    duration = t + TAIL_MS

    fillers = []
    for f in src.get("fillers", []):
        fillers.append({
            "role": f["role"],
            "clip": clip_id(prefix, f["role"], f["text"], used),
            "text": f["text"],
        })

    out = {
        "title": src["title"],
        "durationMs": duration,
        "keyframes": keyframes,
        "cues": cues,
        "fillers": fillers,
        "segments": segments,
    }
    return out, warnings


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("source")
    ap.add_argument("dest")
    ap.add_argument("--measure", action="store_true",
                    help="time the script from rendered voice clips (ffprobe) instead of the estimate")
    args = ap.parse_args()

    src = json.loads(pathlib.Path(args.source).read_text())
    out, warnings = build(src, measure=args.measure)
    dest = pathlib.Path(args.dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n")

    spoken = len(out["cues"]) + len(out["fillers"])
    print(f"{dest.name}: {len(out['cues'])} cues ({spoken} spoken incl. {len(out['fillers'])} fillers), "
          f"{len(out['segments'])} stops, {out['durationMs'] // 60000} min "
          f"{'(measured)' if args.measure else '(estimated)'}")
    for w in warnings:
        print(f"  ! {w}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
