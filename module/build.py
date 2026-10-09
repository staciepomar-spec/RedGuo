# -*- coding: utf-8 -*-
"""免 Gradle 构建红果工具箱 LSPosed 模块 APK。

依赖：JDK(含 keytool) + Android build-tools + platform android.jar，
以及 libxposed api 102 的 classes.jar。所有路径走 ASCII 临时目录以避开
aapt2 对非 ASCII 路径的限制。
"""
import os
import shutil
import subprocess
import sys
import tempfile
import sys
import zipfile

ROOT = r"D:\CEBIANLAN\File\RedGuo"
TOOLS = os.path.join(ROOT, "_tools")
MOD = os.path.join(ROOT, "module")
OUT_DIR = os.path.join(ROOT, "out")
TMP = r"C:\Users\Iss.WR\AppData\Local\Temp\rgmod_build"
KEYS = r"C:\Users\Iss.WR\.workbuddy\keys"
KS = os.path.join(KEYS, "rgmod.jks")
KS_PASS = "android"
KS_ALIAS = "rgmod"

BUILD_TOOLS = os.path.join(TOOLS, "sdk_tmp_bt", "android-16")
ANDROID_JAR = os.path.join(TOOLS, "sdk_tmp_pf", "android-35", "android.jar")
API_JAR = os.path.join(TOOLS, "aar_x", "classes.jar")

JAVA_HOME = r"C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
JAVAC = os.path.join(JAVA_HOME, "bin", "javac.exe")
KEYTOOL = os.path.join(JAVA_HOME, "bin", "keytool.exe")
JAVA = os.path.join(JAVA_HOME, "bin", "java.exe")

MIN_SDK = "29"
TARGET_SDK = "35"
VERSION_CODE = "75"
VERSION_NAME = "2.75"


def run(cmd, cwd=None, check=True):
    print(">>", " ".join(str(c) for c in cmd))
    if cmd[0].lower().endswith((".bat", ".cmd")):
        cmd = ["cmd", "/c"] + cmd
    p = subprocess.run(cmd, cwd=cwd, capture_output=True)
    out = (p.stdout or b"").decode("utf-8", "replace")
    err = (p.stderr or b"").decode("utf-8", "replace")
    if out.strip():
        print(out.strip()[:4000])
    if err.strip():
        print("[stderr]", err.strip()[:4000])
    if check and p.returncode != 0:
        raise SystemExit(f"command failed ({p.returncode}): {cmd}")
    return p.returncode


def tool(name):
    for ext in ("", ".exe", ".bat", ".cmd"):
        p = os.path.join(BUILD_TOOLS, name + ext)
        if os.path.isfile(p):
            return p
    raise SystemExit("tool not found: " + name)





def main():
    for p in (BUILD_TOOLS, ANDROID_JAR, API_JAR, JAVAC):
        if not os.path.exists(p):
            raise SystemExit("missing: " + p)

    if os.path.isdir(TMP):
        shutil.rmtree(TMP, ignore_errors=True)
    os.makedirs(TMP)
    os.makedirs(OUT_DIR, exist_ok=True)

    # 源码拷到 ASCII 临时目录
    src_dir = os.path.join(TMP, "src")
    shutil.copytree(os.path.join(MOD, "src"), src_dir)
    shutil.copy2(os.path.join(MOD, "AndroidManifest.xml"), os.path.join(TMP, "AndroidManifest.xml"))

    # 1a) 资源：目前只有图标。界面仍全部由代码构建，Java 侧不引用 R.*
    #     注意 aapt2 不能处理非 ASCII 路径，res 同样要先拷到 ASCII 临时目录。
    res_src = os.path.join(MOD, "res")
    res_zip = None
    if os.path.isdir(res_src):
        res_dir = os.path.join(TMP, "res")
        shutil.copytree(res_src, res_dir)
        res_zip = os.path.join(TMP, "res.zip")
        run([tool("aapt2"), "compile", "--dir", res_dir, "-o", res_zip])

    # 1b) aapt2 link
    #注意位置参数的**顺序即优先级，靠后的覆盖靠前的**：
    # 模块自己的 res 必须放在 AAR 资源**之后**，否则 mipmap/ic_launcher
    # 会被 AAR 里的同名资源顶掉（实测报 "resource mipmap/ic_launcher not found"）。
    unaligned = os.path.join(TMP, "unaligned.apk")
    link = [tool("aapt2"), "link",
            "-o", unaligned,
            "-I", ANDROID_JAR,
            "--manifest", os.path.join(TMP, "AndroidManifest.xml"),
            "--min-sdk-version", MIN_SDK,
            "--target-sdk-version", TARGET_SDK,
            "--version-code", VERSION_CODE,
            "--version-name", VERSION_NAME,
            "--auto-add-overlay"]
    # v2.83 回到纯 Java 界面（放弃 v2.82 的 miuix/Compose），不再需要 AAR 资源。
    if res_zip:
        link.append(res_zip)
    run(link)

    # 2) javac
    classes_dir = os.path.join(TMP, "classes")
    os.makedirs(classes_dir, exist_ok=True)
    sources = []
    for base, _dirs, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".java"):
                sources.append(os.path.join(base, f))
    print("sources:", len(sources))
    run([JAVAC, "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-nowarn", "-Xlint:none",
         "-classpath", ANDROID_JAR + os.pathsep + API_JAR,
         "-d", classes_dir] + sources)

    # 3) d8 -> dex
    dex_dir = os.path.join(TMP, "dex")
    os.makedirs(dex_dir, exist_ok=True)
    class_files = []
    for base, _dirs, files in os.walk(classes_dir):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(base, f))
    print("javac classes:", len(class_files))
    # Windows 命令行有 8191 字符上限，class 一多就会「命令行太长」。
    # d8 支持 @argfile（每行一个路径），统一走文件传参。
    argfile = os.path.join(TMP, "d8_args.txt")
    with open(argfile, "w", encoding="utf-8") as fh:
        for f in class_files:
            fh.write(f + "\n")
    run([tool("d8"), "--lib", ANDROID_JAR, "--min-api", MIN_SDK,
         "--output", dex_dir, "@" + argfile])

    # 4) 把 dex / assets / META-INF 塞进 apk
    dex_files = sorted(f for f in os.listdir(dex_dir) if f.endswith(".dex"))
    print("dex:", [(f, os.path.getsize(os.path.join(dex_dir, f))) for f in dex_files])
    with zipfile.ZipFile(unaligned, "a", zipfile.ZIP_DEFLATED) as z:
        for f in dex_files:      # 超过 64K 类会产出 classes2.dex…，要全部塞进去
            z.write(os.path.join(dex_dir, f), f)
        assets = os.path.join(MOD, "assets")
        for base, _dirs, files in os.walk(assets):
            for f in files:
                full = os.path.join(base, f)
                rel = os.path.relpath(full, assets).replace("\\", "/")
                z.write(full, "assets/" + rel)
        meta = os.path.join(MOD, "meta")
        for base, _dirs, files in os.walk(meta):
            for f in files:
                full = os.path.join(base, f)
                rel = os.path.relpath(full, meta).replace("\\", "/")
                z.write(full, rel)

    # 5) zipalign + 签名
    aligned = os.path.join(TMP, "aligned.apk")
    run([tool("zipalign"), "-f", "-p", "4", unaligned, aligned])

    if not os.path.isfile(KS):
        os.makedirs(KEYS, exist_ok=True)
        run([KEYTOOL, "-genkeypair", "-keystore", KS, "-alias", KS_ALIAS,
             "-storepass", KS_PASS, "-keypass", KS_PASS,
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-dname", "CN=RGTools, OU=dev, O=wodi, L=CN"])

    out_apk = os.path.join(OUT_DIR, "RGTools-%s.apk" % VERSION_NAME)
    run([tool("apksigner"), "sign",
         "--ks", KS, "--ks-pass", "pass:" + KS_PASS,
         "--ks-key-alias", KS_ALIAS, "--key-pass", "pass:" + KS_PASS,
         "--v1-signing-enabled", "true",
         "--v2-signing-enabled", "true",
         "--v3-signing-enabled", "true",
         "--out", out_apk, aligned])

    run([tool("apksigner"), "verify", "--min-sdk-version", "23", "-v", out_apk], check=False)
    print("\nOK ->", out_apk, os.path.getsize(out_apk), "bytes")


if __name__ == "__main__":
    main()
