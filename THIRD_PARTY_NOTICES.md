# Desktop media tools

SoundTube runs Deno, FFmpeg and FFprobe as separate executables. The desktop release includes the corresponding FFmpeg sources, build recipes and license texts.

- Deno 2.9.8 uses the MIT license. See `Deno-LICENSE.md` and the upstream license for bundled components. [Deno license](https://github.com/denoland/deno/blob/v2.9.8/LICENSE.md)
- FFmpeg and FFprobe use the LGPL v3 or later variant of the 8.1 builds at FFmpeg commit `8dca1586f4`. See `FFmpeg-LICENSE.txt` and `ffmpeg-8dca1586f4-source.tar.gz` in the release assets. [FFmpeg license files](https://github.com/FFmpeg/FFmpeg/tree/8dca1586f468bfcbb78ae3ed618af1361d9f140a)
- The release includes `ffmpeg-build-recipes-5031c6f.tar.gz` with the build recipe and its dependency source references. The original archives list the build configuration and license information. [Upstream FFmpeg builds](https://github.com/BtbN/FFmpeg-Builds/releases/tag/autobuild-2026-10-31-12-59)
