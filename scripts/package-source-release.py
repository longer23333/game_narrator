from pathlib import Path
import zipfile, hashlib, json, shutil

root=Path(__file__).resolve().parents[1]
version='2.2.58'
out=root/'dist'
archive=out/f'GameNarrator-Source-{version}.zip'
manifest_path=out/f'GameNarrator-Source-{version}-manifest.json'

include_roots=['src','frontend','launcher','android-app','browser-extension','.mvn']
include_files=['pom.xml','package.json','README.md','LICENSE','.gitignore','docker-compose.postgresql.yml','mvnw','mvnw.cmd','keystore.properties.example','docs/安装与配置说明.md']
blocked_dirs={'.git','.idea','.codex','.workbuddy','.gradle','.gradle-dist','node_modules','target','build','bin','obj','dist','out','coverage','.cache','cache','logs','storage','data','models','artifacts','keystore','__pycache__','.avd'}
blocked_files={'local.properties','keystore.properties','.env','migration-test.log','baseline-build.log','baseline-build.log.err','vite-mobile-parity.log','vite-mobile-parity-error.log'}
blocked_suffix={'.log','.tmp','.bak','.class','.jar','.apk','.aab','.keystore','.jks','.p12','.zip','.7z','.rar'}

files=[]
for name in include_roots:
    base=root/name
    if not base.exists(): continue
    for p in base.rglob('*'):
        if not p.is_file(): continue
        rel=p.relative_to(root)
        if any(part.lower() in blocked_dirs for part in rel.parts): continue
        wrapper_jar=str(rel).replace('\\','/').lower() in {'.mvn/wrapper/maven-wrapper.jar','android-app/gradle/wrapper/gradle-wrapper.jar'}
        if p.name.lower() in blocked_files or (p.suffix.lower() in blocked_suffix and not wrapper_jar): continue
        files.append(p)
for name in include_files:
    p=root/name
    if p.is_file(): files.append(p)
files=sorted(set(files),key=lambda p:str(p.relative_to(root)).lower())

required=['src/main/java','src/main/resources','frontend/src','frontend/public','launcher/GameNarrator.Launcher.csproj','launcher/Program.cs','android-app/app/src','android-app/build.gradle','android-app/settings.gradle','browser-extension/manifest.json']
for item in required:
    if not (root/item).exists(): raise SystemExit(f'Missing required source: {item}')

readme=f'''GameNarrator {version} 完整源代码包

包含：
- src：Java 21 / Spring Boot 后端、数据库迁移和测试代码
- frontend：Vue 3 / Vite Web前端源码
- launcher：Windows EXE启动器源码
- android-app：Android应用源码及Gradle Wrapper
- browser-extension：浏览器扩展源码

不包含：
- node_modules、target、build、bin、obj等构建产物
- 模型、缓存、日志、用户视频和数据库
- Android签名密钥、local.properties和个人环境配置
- 论文、PPT、截图交付物和项目辅助scripts目录

构建入口：
- 后端：mvnw.cmd package
- Web前端：进入frontend后执行 npm ci 和 npm run build
- Windows启动器：dotnet build launcher\\GameNarrator.Launcher.csproj
- Android：进入android-app后执行 gradlew.bat assembleDebug
'''

if archive.exists(): archive.unlink()
prefix=Path(f'GameNarrator-Source-{version}')
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=9,allowZip64=True) as z:
    z.writestr(str(prefix/'源码包说明.txt').replace('\\','/'),readme.encode('utf-8-sig'))
    for p in files:
        rel=p.relative_to(root)
        target=Path('安装与配置说明.md') if str(rel).replace('\\','/')=='docs/安装与配置说明.md' else rel
        z.write(p,str(prefix/target).replace('\\','/'))

with zipfile.ZipFile(archive) as z:
    bad=z.testzip()
    if bad: raise SystemExit(f'Corrupt entry: {bad}')
    names=z.namelist()
    forbidden=[n for n in names if any('/'+d+'/' in '/'+n.lower()+'/' for d in blocked_dirs) or (Path(n).suffix.lower() in blocked_suffix and not n.lower().endswith(('.mvn/wrapper/maven-wrapper.jar','android-app/gradle/wrapper/gradle-wrapper.jar')))]
    if forbidden: raise SystemExit('Forbidden entries: '+', '.join(forbidden[:20]))
    checks=['src/main/java/','frontend/src/','launcher/Program.cs','android-app/app/src/','browser-extension/manifest.json','安装与配置说明.md']
    for c in checks:
        if not any(c in n for n in names): raise SystemExit(f'Missing archive content: {c}')

sha=hashlib.sha256(archive.read_bytes()).hexdigest()
groups={k:sum(1 for p in files if p.relative_to(root).parts[0]==k) for k in include_roots}
manifest={'version':version,'type':'complete-source','archiveBytes':archive.stat().st_size,'fileCount':len(files)+1,'sha256':sha,'includedGroups':groups,'containsAndroidSource':True,'containsWindowsLauncherSource':True,'containsWebSource':True,'containsBackendSource':True,'containsBrowserExtensionSource':True,'excluded':['build outputs','dependency caches','models','logs','user data','signing keys','helper scripts','documents']}
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(manifest,ensure_ascii=False))
