# -*- coding: utf-8 -*-
"""按 POM 递归解析并下载 Compose/miuix 的完整依赖树。

为什么需要它：手工一个个猜坐标极其低效（同一个 artifact 有
`xxx` / `xxx-android` 两种命名，猜错就是 404）。这个脚本读 POM 的
<dependency> 节点递归展开，产出 `_tools/miuix_deps/cp/`（全部 classes.jar）
与 `_tools/miuix_deps/deps.lock.json`（锁定解析结果）。

只收 compile/runtime 作用域的 aar/jar；test/lint 一律跳过。
"""
import json
import os
import re
import subprocess
import sys
import zipfile

ROOT = r"D:\CEBIANLAN\File\RedGuo"
DEPS = os.path.join(ROOT, "_tools", "miuix_deps")
CP = os.path.join(DEPS, "cp")
LOCK = os.path.join(DEPS, "deps.lock.json")

GMAVEN = "https://maven.aliyun.com/repository/google"
CENTRAL = "https://repo1.maven.org/maven2"

# 根依赖：miuix + 它的直接依赖
ROOTS = [
    ("top.yukonga.miuix.kmp", "miuix-ui-android", "0.9.4"),
    ("top.yukonga.miuix.kmp", "miuix-core-android", "0.9.4"),
    ("top.yukonga.miuix.kmp", "miuix-squircle-android", "0.9.4"),
    ("com.materialkolor", "material-color-utilities-android", "5.0.1"),
]

SKIP_ARTIFACT = re.compile(
    r"(test|lint|sources|javadoc|annotations)-",re.I)

visited = {}


def sh(args):
    return subprocess.run(args, capture_output=True)


def fetch_pom(group, artifact, version):
    path = "%s/%s/%s/%s-%s.pom" % (group.replace(".", "/"), artifact, version, artifact, version)
    for base in (GMAVEN, CENTRAL):
        p = sh(["curl", "-sL", "-m", "60", base + "/" + path])
        body = p.stdout.decode("utf-8", "replace")
        if "<project" in body or "<dependency" in body:
            return body
    return None


def parse_deps(pom):
    out = []
    for m in re.finditer(r"<dependency>(.*?)</dependency>", pom, re.S):
        b = m.group(1)

        def f(tag):
            r = re.search(r"<%s>([^<]+)" % tag, b)
            return r.group(1).strip() if r else None

        g, a, v = f("groupId"), f("artifactId"), f("version")
        scope = f("scope") or "compile"
        if not g or not a:
            continue
        if scope in ("test", "provided"):
            continue
        if SKIP_ARTIFACT.search(a):
            continue
        out.append((g, a, v, scope))
    return out


def fetch_artifact(group, artifact, version):
    """按 aar -> jar 顺序试；返回本地文件名或 None。"""
    base = "%s/%s/%s/%s-%s" % (group.replace(".", "/"), artifact, version, artifact, version)
    for repo, ext in ((GMAVEN, "aar"), (CENTRAL, "aar"), (CENTRAL, "jar"), (GMAVEN, "jar")):
        out = os.path.join(DEPS, "%s-%s.%s" % (artifact, version, ext))
        if os.path.exists(out) and os.path.getsize(out) > 2000:
            return out
        r = sh(["curl", "-sL", "-m", "180", "-o", out, repo + "/" + base + "." + ext])
        if r.returncode == 0 and os.path.exists(out) and os.path.getsize(out) > 2000:
            head = open(out, "rb").read(4)
            if head[:2] == b"PK":          # 是真 zip，不是 404 HTML
                return out
        if os.path.exists(out):
            os.remove(out)
    return None


def extract_classes(artifact_path, group, artifact, version):
    """AAR 取 classes.jar，jar 直接用；统一落成 cp/<artifact>.jar。"""
    tgt = os.path.join(CP, "%s.jar" % artifact)
    if os.path.exists(tgt):
        return tgt
    if artifact_path.endswith(".aar"):
        try:
            z = zipfile.ZipFile(artifact_path)
            if "classes.jar" not in z.namelist():
                return None
            data = z.read("classes.jar")
        except Exception:
            return None
        if len(data) < 200:
            return None          # KMP 空壳（只有转发），实现另在别处
    else:
        with open(artifact_path, "rb") as fh:
            data = fh.read()
    with open(tgt, "wb") as fh:
        fh.write(data)
    return tgt


def resolve(group, artifact, version, depth=0):
    # 必须**进入时立刻标记**：KMP 的依赖图有环（ui -> ui-geometry -> ui …），
    # 若等到函数末尾才标记，同一坐标会被递归展开无数次直到爆栈/输出刷屏。
    key = "%s:%s:%s" % (group, artifact, version)
    if key in visited:
        return
    visited[key] = True
    pom = fetch_pom(group, artifact, version)
    if not pom:
        print("  " * depth + "[pom缺失] " + key)
        return
    for g, a, v, scope in parse_deps(pom):
        ver = (v or "").strip("[]")
        if not ver or ver.startswith("$"):
            continue
        resolve(g, a, ver, depth + 1)
    art = fetch_artifact(group, artifact, version)
    if art:
        if extract_classes(art, group, artifact, version):
            print("  " * depth + "+ %s:%s:%s" % (group, artifact, version))


def main():
    os.makedirs(CP, exist_ok=True)
    for g, a, v in ROOTS:
        print("解析 %s:%s:%s" % (g, a, v))
        resolve(g, a, v)
    with open(LOCK, "w", encoding="utf-8") as fh:
        json.dump(sorted(visited), fh, indent=1, ensure_ascii=False)
    print("\ncp jar数:", len(os.listdir(CP)))


if __name__ == "__main__":
    main()