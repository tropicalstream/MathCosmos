VOICES
======
The crew are pre-rendered Fish Audio clips. The X3 Pro has NO speech engine bound, so a line
with no clip is shown as a caption and heard as nothing (CrewVoices.speakSilently) — the tour
is watchable without audio, it is just quiet.

To render them you need two things this repository deliberately does not contain:

1. app/tools/fish_audio.config — the three reference voice ids, so HELM, DOC and ENGINEERING
   sound like three different people. The sibling project already has one:

       cp ~/Projects/InnerCosmos/app/tools/fish_audio.config ~/Projects/MathCosmos/app/tools/

   (Do that yourself: it holds an API key, and the assistant is not permitted to copy it.)

2. A valid API key. The one inside that file was dead as of 2026-09-01; the working key lives
   in ~/Projects/x3cycles/tools/fish.config and is passed at run time, never copied in:

       cd ~/Projects/MathCosmos
       python3 app/tools/generate_fish_audio.py \
           --script app/src/main/assets/tour1_script.json \
           --key-from ~/Projects/x3cycles/tools/fish.config \
           --model s2.1-pro

Then re-time the script against the audio that actually came back, so no line is talked over
by the one behind it:

       python3 app/tools/build_script.py script_src/tour1.json \
           app/src/main/assets/tour1_script.json --measure
       python3 app/tools/validate_script.py app/src/main/assets/tour1_script.json

After any voice change: rebuild, `adb -s A06B4A96A733283 shell pm clear com.rayneo.mathcosmos`,
reinstall. CrewVoices drops its cache when the APK's lastUpdateTime changes, but clearing is
the certain way.

VOICES — THE FREE MODEL
=======================
Fish Audio makes its current best model free to developers: pass the model string

    s2.1-pro-free

instead of s2.1-pro and the same S2.1 Pro backbone renders at no cost, with the same reference
voice ids, under a Fair Use policy and with no hard character cap. The whole series — 879 lines —
was rendered this way. Checked on identical text, free and paid came back within 4% of each other
in length and use the same three voices, so nothing about the crew changes.

Two things worth knowing before leaning on it:

  * It is a promotion with an end date, extended twice already (most recently to 31 August 2026)
    and still answering afterwards. If it stops, drop --model back to s2.1-pro and pay.
  * The Fair Use terms say requests may be retained and used to improve the model, and ask
    products over $1M ARR to make contact first. The scripts here are the app's own dialogue, so
    that is a fair trade; do not push anything confidential through the free tier.

    cd ~/Projects/MathCosmos
    for n in 1 2 3 4 5 6; do
      python3 app/tools/generate_fish_audio.py \
        --script app/src/main/assets/tour${n}_script.json \
        --key-from ~/Projects/x3cycles/tools/fish.config --model s2.1-pro-free
    done

The key inside app/tools/fish_audio.config is dead (401); that file is there for the three
reference voice ids. The working key is the one --key-from points at.

ALWAYS re-time after rendering, or a line will be talked over by the one behind it:

    for n in 1 2 3 4 5 6; do
      python3 app/tools/build_script.py script_src/tour$n.json \
        app/src/main/assets/tour${n}_script.json --measure
      python3 app/tools/validate_script.py app/src/main/assets/tour${n}_script.json
    done

Then rebuild, `adb -s A06B4A96A733283 shell pm clear com.rayneo.mathcosmos`, reinstall.

CLIP IDS ARE CONTENT-ADDRESSED
==============================
A clip is named for its role, its first few words, and a digest of the whole line. Edit any part
of a line and its id changes, so the stale file is orphaned and a fresh one is rendered. Before
that digest existed, revising the back half of a line left the id alone, the generator skipped it,
and thirty-eight clips shipped saying the sentence they had replaced — inaudible until you wear
the glasses and hear a line disagree with its own caption.
