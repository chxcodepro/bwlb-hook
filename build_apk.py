"""Build the LSPosed APK with Android SDK tools, a JDK and Zig 0.13.0."""

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
WINDOWS = os.name == 'nt'

def executable(name):
    return name + ('.exe' if WINDOWS else '')

def toolchain():
    default_sdk = Path.home() / ('AppData/Local/Android/Sdk' if WINDOWS else 'Library/Android/sdk' if os.sys.platform == 'darwin' else 'Android/Sdk')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or default_sdk)
    bt = next((sdk / 'build-tools' / v for v in ('35.0.0', '34.0.0')
               if (sdk / 'build-tools' / v / executable('aapt2')).is_file()), None)
    platform = sdk / 'platforms/android-34/android.jar'
    java = os.environ.get('JAVA_HOME')
    jdk = Path(java) / 'bin' if java else Path(shutil.which(executable('javac')) or '').resolve().parent
    zig = os.environ.get('CARROT_ZIG') or shutil.which(executable('zig'))
    if not zig:
        try:
            import ziglang
            zig = str(Path(ziglang.__file__).parent / executable('zig'))
        except ImportError:
            pass
    if not bt or not platform.is_file() or not (jdk / executable('javac')).is_file() or not zig or not Path(zig).is_file():
        raise RuntimeError('Install Android SDK 34, build tools 35.0.0, JDK 21 and Zig 0.13.0; see README.md')
    return bt, platform, jdk, Path(zig)

def run(args):
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if result.returncode:
        print((result.stdout + result.stderr)[-6000:])
        raise RuntimeError('Build tool failed: ' + Path(args[0]).name)
    return result.stdout

def build(work, bt, platform, jdk, zig):
    version = (ROOT / 'VERSION').read_text(encoding='utf-8').strip()
    manifest = ET.parse(ROOT / 'app/AndroidManifest.xml').getroot()
    if manifest.get('{http://schemas.android.com/apk/res/android}versionName') != version:
        raise RuntimeError('VERSION and AndroidManifest versionName must agree')
    for folder in ('src', 'res', 'assets', 'libs', 'native'):
        shutil.copytree(ROOT / 'app' / folder, work / folder)
    shutil.copy2(ROOT / 'app/AndroidManifest.xml', work / 'AndroidManifest.xml')
    output_dir = work / 'build'
    for folder in ('gen', 'classes', 'dex', 'native'):
        (output_dir / folder).mkdir(parents=True)
    aapt = bt / executable('aapt2')
    run([aapt, 'compile', '--dir', work / 'res', '-o', output_dir / 'res.zip'])
    run([aapt, 'link', '-o', output_dir / 'base.apk', '-I', platform, '--manifest', work / 'AndroidManifest.xml',
         '--java', output_dir / 'gen', '--min-sdk-version', '30', '--target-sdk-version', '34', output_dir / 'res.zip'])
    sources = sorted((work / 'src').rglob('*.java')) + sorted((output_dir / 'gen').rglob('*.java'))
    run([jdk / executable('javac'), '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-nowarn',
         '-classpath', str(platform) + os.pathsep + str(work / 'libs/xposed-api-82.jar'), '-d', output_dir / 'classes'] + sources)
    run([jdk / executable('java'), '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', platform,
         '--min-api', '30', '--output', output_dir / 'dex'] + sorted((output_dir / 'classes').rglob('*.class')))
    names = ['libcarrot1.so', 'libcoinlock.so', 'libcarrot3.so', 'libcarrot4.so']
    for game, name in enumerate(names, 1):
        source = work / 'native' / ('game' + str(game))
        native = output_dir / 'native' / ('game' + str(game)); native.mkdir()
        common = [zig, 'cc', '-target', 'aarch64-linux-android', '-shared', '-nostdlib', '-fPIC']
        if game > 1:
            for kind, stub in [('LIBC', 'libc.so'), ('DL', 'libdl.so')]:
                run(common + ['-DSTUB_' + kind, '-Wl,-soname,' + stub, source / 'link_stubs.c', '-o', native / stub])
        if game == 4:
            lua = (source / 'controller.lua').read_text(encoding='utf-8')
            (native / 'controller.h').write_text('static const char CONTROLLER[] = ' + json.dumps(lua, ensure_ascii=False) + ';\n', encoding='utf-8')
        args = common + ['-O2', '-s', '-ffreestanding', '-fno-stack-protector', '-Wl,-soname,' + name,
                         '-Wl,--hash-style=both', '-Wl,-z,max-page-size=16384', '-Wl,--allow-shlib-undefined',
                         '-I' + str(native), source / 'coinlock.c', '-o', native / name]
        if game > 1:
            args += ['-L' + str(native), '-Wl,--no-as-needed', '-l:libdl.so', '-l:libc.so']
        run(args)
        print('Native', game, 'OK')
    with zipfile.ZipFile(output_dir / 'base.apk', 'a', zipfile.ZIP_DEFLATED) as apk:
        apk.write(output_dir / 'dex/classes.dex', 'classes.dex')
        apk.write(work / 'assets/xposed_init', 'assets/xposed_init')
        for game, name in enumerate(names, 1):
            apk.write(output_dir / 'native' / ('game' + str(game)) / name, 'lib/arm64-v8a/' + name)
    run([bt / executable('zipalign'), '-P', '16', '-f', '4', output_dir / 'base.apk', output_dir / 'aligned.apk'])
    key = Path(os.environ.get('ANDROID_KEYSTORE') or ROOT / '.signing/suite.keystore')
    alias = os.environ.get('ANDROID_KEY_ALIAS', 'suite')
    require_key = os.environ.get('ANDROID_REQUIRE_RELEASE_KEY') == 'true'
    if require_key and not key.is_file():
        raise RuntimeError('A release signing key is required')
    for env in ('ANDROID_KEYSTORE_PASSWORD', 'ANDROID_KEY_PASSWORD'):
        if require_key and not os.environ.get(env):
            raise RuntimeError('Missing signing configuration: ' + env)
        os.environ.setdefault(env, 'android')
    if not key.is_file():
        key.parent.mkdir(parents=True, exist_ok=True)
        run([jdk / executable('keytool'), '-genkeypair', '-keystore', key, '-alias', alias,
             '-storepass:env', 'ANDROID_KEYSTORE_PASSWORD', '-keypass:env', 'ANDROID_KEY_PASSWORD',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Carrot Suite'])
    dist = ROOT / 'dist'; dist.mkdir(exist_ok=True)
    output = dist / '保卫萝卜合集.apk'
    signer = [jdk / executable('java'), '-jar', bt / 'lib/apksigner.jar']
    run(signer + ['sign', '--ks', key, '--ks-key-alias', alias, '--ks-pass', 'env:ANDROID_KEYSTORE_PASSWORD',
                  '--key-pass', 'env:ANDROID_KEY_PASSWORD', '--out', output, output_dir / 'aligned.apk'])
    print(run(signer + ['verify', '--verbose', output]).strip())
    run([bt / executable('zipalign'), '-c', '-P', '16', '4', output])
    with zipfile.ZipFile(output) as apk:
        if apk.testzip():
            raise RuntimeError('APK archive verification failed')
        required = {'classes.dex', 'AndroidManifest.xml', 'assets/xposed_init'} | {'lib/arm64-v8a/' + name for name in names}
        if not required.issubset(apk.namelist()):
            raise RuntimeError('APK is missing required files')
        for info in apk.infolist():
            if info.filename.startswith('lib/'):
                data = apk.read(info.filename)
                if data[:5] != b'\x7fELF\x02' or int.from_bytes(data[18:20], 'little') != 183:
                    raise RuntimeError('Unexpected native architecture')
    checksum = hashlib.sha256(output.read_bytes()).hexdigest()
    (dist / (output.name + '.sha256')).write_text(checksum + '  ' + output.name + '\n', encoding='utf-8')
    print('APK:', output, output.stat().st_size, 'bytes')
    print('SHA-256:', checksum)

def main():
    bt, platform, jdk, zig = toolchain()
    if run([zig, 'version']).strip() != '0.13.0':
        raise RuntimeError('Zig 0.13.0 is required')
    with tempfile.TemporaryDirectory(prefix='bwlb-build-') as temp:
        build(Path(temp), bt, platform, jdk, zig)

if __name__ == '__main__':
    main()
