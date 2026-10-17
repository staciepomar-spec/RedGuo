# -*- coding: utf-8 -*-
"""冒烟构建：把 ProbeActivity（Kotlin + Compose + miuix）打成独立 APK 装真机。

用途：验证 kotlinc -> d8 -> aapt2 -> 签名 这条链能否在真机上真正渲染 miuix 底栏。
与模块本体构建（module/build.py）分开，避免每次调试都重编 2 万个依赖类。
"""
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = r"D:\CEBIANLAN\File\RedGuo"
TOOLS = os.path.join(ROOT, "_tools")
KT = os.path.join(TOOLS, "kotlin")
DEPS = os.path.join(TOOLS, "miix_deps" if os.path.isdir(os.path.join(TOOLS, "miix_deps")) else "miuix_deps")
CP_DIR = os.path.join(DEPS, "cp")
BUILD_TOOLS = os.path.join(TOOLS, "sdk_tmp_bt", "android-16")
ANDROID_JAR = os.path.join(TOOLS, "sdk_tmp_pf", "android-35", "android.jar")
KS = r"C:\Users\Iss.WR\.workbuddy\keys\rgmod.jks"

#Git Bash 的 /tmp 与 Java 看到的路径不通，d8 必须用 Windows 绝对路径
WORK = r"C:\Users\Iss.WR\AppData\Local\Temp\rgcompose"
SRC = os.path.join(ROOT, "module", "kt")

JAVA = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot\bin\java.exe"


def run(cmd, cwd=None, check=True):
    print(">>", " ".join(str(c) for c in cmd[:6]), "..." if len(cmd) > 6 else "")
    if str(cmd[0]).lower().endswith((".bat", ".cmd")):
        cmd = ["cmd", "/c"] + cmd
    p = subprocess.run(cmd, cwd=cwd, capture_output=True)
    out = (p.stdout or b"").decode("utf-8", "replace")
    err = (p.stderr or b"").decode("utf-8", "replace")
    if out.strip():
        print(out.strip()[:3000])
    if err.strip():
        print("[stderr]", err.strip()[:3000])
    if check and p.returncode != 0:
        raise SystemExit("failed: %s" % (cmd[:3],))
    return p.returncode


def tool(name):
    for ext in ("", ".exe", ".bat", ".cmd"):
        p = os.path.join(BUILD_TOOLS, name + ext)
        if os.path.isfile(p):
            return p
    raise SystemExit("tool not found: " + name)


def classpath():
    jars = [os.path.join(CP_DIR, f) for f in sorted(os.listdir(CP_DIR)) if f.endswith(".jar")]
    return ";".join(jars)


def aar_flat_zips(work):
    """依赖 AAR 的编译资源（flat zip）。

    AAR 里只有 R.txt（资源名清单），没有编译好的 R.class —— R 类正常由 Gradle
    在构建 AAR 时生成。手工构建缺这一步，`PoolingContainer` 之类引用
    `R$id` 的类就会在真机上 NoClassDefFoundError。
    所以必须把这些 res 交给 aapt2 一起 link，R 类才会被生成。

    缓存在 _tools/miuix_deps/flat 下（依赖不变就不用重编）。
    """
    cache = os.path.join(DEPS, "flat")
    stamp = os.path.join(cache, "flat_list.txt")
    if os.path.isfile(stamp):
        with open(stamp, encoding="utf-8") as fh:
            paths = [x.strip() for x in fh if x.strip()]
        if paths and all(os.path.isfile(p) for p in paths):
            return paths
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import aar_res
    if not os.path.isdir(os.path.join(work, "aarres")):
        aar_res.extract(work)
    flats = aar_res.compile_res(work)
    # 把产物挪到缓存目录，避免 WORK 被清空后失效
    if os.path.isdir(cache):
        shutil.rmtree(cache, ignore_errors=True)
    shutil.copytree(os.path.join(work, "aarres"), os.path.join(cache, "aarres"))
    for f in os.listdir(work):
        if f.startswith("flat_") and f.endswith(".zip"):
            shutil.copy2(os.path.join(work, f), os.path.join(cache, f))
    with open(os.path.join(cache, "flat_list.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(os.path.join(cache, os.path.basename(p)) for p in flats))
    return [os.path.join(cache, os.path.basename(p)) for p in flats]


def dep_packages():
    """需要单独生成 R.java 的依赖包名（--extra-packages 的参数）。

    读各 AAR 的 AndroidManifest package。**必须排除 org.jetbrains.* 的 KMP 空壳** ——
    它们只是转发到 androidx 的壳，R 类由 androidx 侧提供，
    把它们也列进去会生成一份没人用的 R。
    """
    import re
    out = set()
    deps = os.path.join(TOOLS, "miuix_deps")
    for f in sorted(os.listdir(deps)):
        if not f.endswith(".aar"):
            continue
        try:
            mf = zipfile.ZipFile(os.path.join(deps, f)).read("AndroidManifest.xml")
        except Exception:
            continue
        m = re.search(rb'package="([^"]+)"', mf)
        if not m:
            continue
        pkg = m.group(1).decode("utf-8", "replace")
        if pkg.startswith("org.jetbrains"):
            continue
        out.add(pkg)
    return out


def write_manifest(path):
    """探针用的清单（只挂 MainActivity 一个入口）。"""
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    package="com.wodi.probe">\n'
            '  <application android:label="Probe"\n'
            '      android:theme="@android:style/Theme.Material.Light.NoActionBar">\n'
            '    <activity android:name="com.wodi.redguotools.MainActivity" android:exported="true">\n'
            '      <intent-filter>\n'
            '        <action android:name="android.intent.action.MAIN" />\n'
            '        <category android:name="android.intent.category.LAUNCHER" />\n'
            '      </intent-filter>\n'
            '    </activity>\n'
            '  </application>\n'
            '</manifest>\n'
        )
    return path


def main():
    if os.path.isdir(WORK):
        shutil.rmtree(WORK, ignore_errors=True)
    os.makedirs(os.path.join(WORK, "kt"))
    os.makedirs(os.path.join(WORK, "dex"))

    # 1) Kotlin + Compose plugin
    #    Kotlin 源码会调用 Java 侧的 RootShell（读日志），所以先把 Java 编一遍，
    #    再把classes_dir 挂到 kotlinc 的 classpath 上。
    classes_dir = os.path.join(WORK, "classes")
    os.makedirs(classes_dir, exist_ok=True)
    java_src = os.path.join(ROOT, "module", "src")
    javac = os.path.join(r"C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot",
                         "bin", "javac.exe")
    api_jar = os.path.join(TOOLS, "aar_x", "classes.jar")

    # 1a) 生成依赖包的 R.java（AAR 里只有 R.txt，R 类正常由 AGP 生成，手工构建缺这一步）
    gen_r_dir = os.path.join(WORK, "genr")
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import gen_r
    manifest0 = os.path.join(WORK, "AndroidManifest.xml")
    write_manifest(manifest0)
    flats = aar_flat_zips(WORK)
    import tempfile as _tf
    _w = _tf.mkdtemp(prefix="rg_r_")
    _ids = gen_r.emit_ids(manifest0, flats, _w)
    claim = gen_r.attribute_to_aars(gen_r.parse_ids(_ids))
    for pkg, classes in claim.items():
        gen_r.gen_java(pkg, classes, gen_r_dir)
    print("generated R.java:", len(claim))

    jsrcs = []
    for base, _d, files in os.walk(java_src):
        for f in files:
            if f.endswith(".java"):
                jsrcs.append(os.path.join(base, f))
    for base, _d, files in os.walk(gen_r_dir):
        for f in files:
            if f.endswith(".java"):
                jsrcs.append(os.path.join(base, f))
    print("javac sources:", len(jsrcs))
    run([javac, "-encoding", "UTF-8", "-nowarn", "-Xlint:none",
         "-classpath", ANDROID_JAR + ";" + api_jar, "-d", classes_dir] + jsrcs)

    kcp = ";".join([
        os.path.join(KT, "kce.jar"),
        os.path.join(KT, "coroutines.jar"),
        os.path.join(KT, "trove4j.jar"),
        os.path.join(KT, "script-rt.jar"),
        os.path.join(KT, "daemon.jar"),
        os.path.join(KT, "annotations.jar"),
        os.path.join(DEPS, "kotlin-stdlib.jar"),
    ])
    sources = []
    for base, _d, files in os.walk(SRC):
        for f in files:
            if f.endswith(".kt"):
                sources.append(os.path.join(base, f))
    print("kotlin sources:", len(sources))
    run([JAVA, "-cp", kcp, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
         "-no-stdlib", "-jvm-target", "17",
         "-classpath", ANDROID_JAR + ";" + classpath() + ";" + classes_dir,
         "-Xplugin=" + os.path.join(KT, "compose.jar"),
         "-d", os.path.join(WORK, "kt")] + sources)

    # 2) d8：本模块类（Java + Kotlin）+ 全部依赖（Compose 运行时必须一起打进去）
    argfile = os.path.join(WORK, "d8.txt")
    entries = []
    for root in (classes_dir, os.path.join(WORK, "kt")):
        for base, _d, files in os.walk(root):
            for f in files:
                if f.endswith(".class"):
                    entries.append(os.path.join(base, f))
    entries += [os.path.join(CP_DIR, f) for f in sorted(os.listdir(CP_DIR)) if f.endswith(".jar")]
    with open(argfile, "w", encoding="utf-8") as fh:
        fh.write("\n".join(entries))
    print("d8 entries:", len(entries))
    run([tool("d8"), "--lib", ANDROID_JAR, "--min-api", "29",
         "--output", os.path.join(WORK, "dex"), "@" + argfile])

    # 3) aapt2 link（清单在1a 步已写好）
    manifest = manifest0
    base_apk = os.path.join(WORK, "base.apk")
    # 依赖 AAR 的资源（见 aar_flat_zips 的说明）：必须参与 link，
    # 否则 aapt2 不会为它们生成 R 类，真机启动即崩。
    # --extra-packages 让 aapt2 为每个依赖包名各生成一份 R.java：
    #   androidx.customview.poolingcontainer.R$id 等。
    # 没这个参数时它们会被合并进 App 自己的包（--no-static-lib-packages 的行为），
    # 于是 poolingcontainer 找不到自己的 R —— 实测就崩在这。
    res_zip = None
    flats = aar_flat_zips(WORK)
    extra_pkgs = sorted({p for p in dep_packages()})
    link = [tool("aapt2"), "link", "-o", base_apk, "-I", ANDROID_JAR,
            "--manifest", manifest, "--min-sdk-version", "29",
            "--target-sdk-version", "35", "--version-code", "1",
            "--version-name", "1.0", "--auto-add-overlay"]
    for p in extra_pkgs:
        link += ["--extra-packages", p]
    if res_zip:
        link.append(res_zip)
    link += flats
    run(link)

    # 4) 把所有 dex 塞进 apk（>64K 类会产出 classes2.dex…）
    apk = os.path.join(WORK, "probe.apk")
    shutil.copy2(base_apk, apk)
    dex_dir = os.path.join(WORK, "dex")
    with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(os.listdir(dex_dir)):
            if f.endswith(".dex"):
                z.write(os.path.join(dex_dir, f), f)

    # 5) 对齐 + 签名
    aligned = os.path.join(WORK, "probe_al.apk")
    run([tool("zipalign"), "-f", "-p", "4", apk, aligned])
    out = os.path.join(WORK, "probe_signed.apk")
    run([tool("apksigner"), "sign", "--ks", KS, "--ks-pass", "pass:android",
         "--ks-key-alias", "rgmod", "--key-pass", "pass:android",
         "--out", out, aligned])
    print("\nOK ->", out, os.path.getsize(out), "bytes")


if __name__ == "__main__":
    main()