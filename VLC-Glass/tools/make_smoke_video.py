from pathlib import Path
import subprocess
import imageio_ffmpeg
path=Path('build/safe-smoke.mp4');path.parent.mkdir(exist_ok=True)
subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-hide_banner','-loglevel','error','-y','-f','lavfi','-i','testsrc2=s=320x180:r=24:d=7','-c:v','libx264','-pix_fmt','yuv420p',str(path)],check=True)
print(path)
