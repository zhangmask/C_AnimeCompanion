# Local AARs

Place the official `sherpa-onnx-1.13.0.aar` Android release from k2-fsa in this directory before building a device package with local SenseVoice ASR enabled.

The AAR is intentionally ignored by Git because it is large. ASR model files are also not packaged in the APK; keep the local project cache under:

`third_party/models/asr/sensevoice/`

Push those files to the device directory:

`context.getExternalFilesDir("models/asr/sensevoice")`
