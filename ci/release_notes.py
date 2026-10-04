import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
tag = sys.argv[1]
version = (ROOT / 'VERSION').read_text(encoding='utf-8').strip()
manifest = ET.parse(ROOT / 'app/AndroidManifest.xml').getroot()
manifest_version = manifest.get('{http://schemas.android.com/apk/res/android}versionName')
if tag != 'v' + version or manifest_version != version:
    raise SystemExit('Tag, VERSION and AndroidManifest versionName must agree')
changelog = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')
match = re.search(r'^## ' + re.escape(version) + r' - [^\n]+\n(.*?)(?=^## |\Z)', changelog, re.M | re.S)
if not match or not match.group(1).strip():
    raise SystemExit('Missing changelog section for ' + version)
notes = '# 保卫萝卜合集 ' + tag + '\n\n' + match.group(1).strip()
notes += '\n\n## 安装\n\n下载 APK，在 LSPosed 中启用合集并勾选对应游戏；关闭旧版单代模块，重新启动游戏。\n'
notes += '\nAndroid 11+ · ARM64。`.sha256` 文件用于校验下载文件。\n'
if len(sys.argv) > 2:
    Path(sys.argv[2]).write_text(notes, encoding='utf-8')
else:
    print(notes)
