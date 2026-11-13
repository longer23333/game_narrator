from pathlib import Path
import shutil, zipfile, hashlib, os, json

root=Path(__file__).resolve().parents[1]
src=root/'release'/'staging'
version='2.2.58'
dist=root/'dist'
portable=dist/f'GameNarrator-Portable-{version}'
archive=dist/f'GameNarrator-Portable-{version}-win-x64.zip'
required=['GameNarrator.exe','app/game-narrator.jar','runtime/bin/java.exe','tools/ffmpeg/bin/ffmpeg.exe','models/whisper/ggml-base.bin','models/piper/zh_CN-huayan-medium.onnx']
for item in required:
    if not (src/item).is_file(): raise SystemExit(f'Missing release file: {item}')
if portable.exists(): shutil.rmtree(portable)
portable.mkdir(parents=True)
for name in ['GameNarrator.exe','app','runtime','tools','models','prerequisites','licenses','dependency-lock.json']:
    p=src/name
    if not p.exists(): continue
    shutil.copytree(p,portable/name) if p.is_dir() else shutil.copy2(p,portable/name)
readme='''GameNarrator 2.2.58 Windows 便携版

使用方法：
1. 将整个压缩包解压到非系统盘，例如 G:\\GameNarrator。
2. 双击 GameNarrator.exe。
3. 首次运行会检查本地依赖；若缺少 WebView2，可运行 prerequisites 目录中的安装程序。
4. Whisper 与 Piper 模型已经包含。视觉模型由程序通过 Ollama 检测并按需下载，请把模型目录设置到非系统盘。

注意：不要只复制 exe，app、runtime、tools 和 models 目录必须与它放在一起。
用户项目、缓存和日志会在运行后写入所配置的数据目录，本压缩包不包含开发缓存或个人数据。
'''
(portable/'使用说明.txt').write_text(readme,encoding='utf-8-sig')
blocked_suffix={'.ps1','.cmd','.bat','.log','.tmp','.bak','.class','.java','.ts','.vue'}
blocked_names={'node_modules','.git','.gradle','target','cache','staging','__pycache__'}
bad=[]
for p in portable.rglob('*'):
    rel=p.relative_to(portable)
    if any(part.lower() in blocked_names for part in rel.parts) or (p.is_file() and p.suffix.lower() in blocked_suffix): bad.append(str(rel))
if bad: raise SystemExit('Unexpected development files: '+', '.join(bad[:20]))
if archive.exists(): archive.unlink()
files=[p for p in portable.rglob('*') if p.is_file()]
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as z:
    for p in files:z.write(p,Path(portable.name)/p.relative_to(portable))
with zipfile.ZipFile(archive) as z:
    corrupt=z.testzip()
    if corrupt: raise SystemExit(f'Corrupt zip entry: {corrupt}')
    names=set(z.namelist())
    for item in required:
        expected=str(Path(portable.name)/item).replace('\\','/')
        if expected not in names: raise SystemExit(f'Missing zip entry: {expected}')
sha=hashlib.sha256(archive.read_bytes()).hexdigest()
size=sum(p.stat().st_size for p in files)
manifest={'version':version,'platform':'windows-x64','fileCount':len(files),'unpackedBytes':size,'archiveBytes':archive.stat().st_size,'sha256':sha,'entry':'GameNarrator.exe','excluded':['source code','build scripts','node_modules','release cache','logs','temporary files','user data']}
(dist/f'GameNarrator-Portable-{version}-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(manifest,ensure_ascii=False))
