# -*- coding: utf-8 -*-
"""由 aapt2 的 --emit-ids 输出生成各依赖包的 R.java。

**背景**：AAR 里只有 R.txt（资源名清单）和编译好的资源，**没有 R.class**——
R 类正常由 Gradle/AGP 在构建 AAR 时生成。手工构建没有 AGP，于是
`androidx.customview.poolingcontainer.PoolingContainer` 这类引用了
`R$id` 的类在真机上直接 `NoClassDefFoundError`。

`aapt2 link --extra-packages <pkg>` 会为每个包名各生成一份 R.java，
但**只输出到 stdout，不落盘**；`--emit-ids` 则给出完整的
`包名:类型/名字 = 0xID` 映射。所以做法是：

1. `aapt2 link --emit-ids ids.txt --extra-packages <各依赖包>`
2. 读 ids.txt，按包名+类型分组，生成 `R.java`（每个包一份）
3. 这些 .java 与模块源码一起 javac，R 类自然进dex

注意：AndroidX 各库的 R 类在运行时**不会**被真正反射用到（Compose 全部走代码），
它们只要「存在且字段值对得上」即可，但字段值必须与 aapt2 分配的一致，
否则 `View.setTag(int, …)` 之类会错。所以必须读 emit-ids，不能手写常量。
"""
import os
import re
import subprocess
import sys
import tempfile

TOOLS = r"D:\CEBIANLAN\File\RedGuo\_tools"
DEPS = os.path.join(TOOLS, "miuix_deps")
AAPT2 = os.path.join(TOOLS, "sdk_tmp_bt", "android-16", "aapt2.exe")
ANDROID_JAR = os.path.join(TOOLS, "sdk_tmp_pf", "android-35", "android.jar")

# 不需要 R 的包：纯代码库，或我们自己的包（自己的 R 由 AGP 无关，运行时也用不到）
SKIP_PKG_PREFIX = ("com.wodi", "org.jetbrains")


def dep_packages():
    """需要生成 R.java 的包名（从 AAR 的 AndroidManifest 读）。"""
    out = []
    for f in sorted(os.listdir(DEPS)):
        if not f.endswith(".aar"):
            continue
        try:
            import zipfile
            mf = zipfile.ZipFile(os.path.join(DEPS, f)).read("AndroidManifest.xml")
        except Exception:
            continue
        m = re.search(rb'package="([^"]+)"', mf)
        if not m:
            continue
        pkg = m.group(1).decode("utf-8", "replace")
        if pkg.startswith(SKIP_PKG_PREFIX):
            continue
        if pkg not in out:
            out.append(pkg)
    return out


def emit_ids(manifest, flats, work, res_zip=None):
    """跑一次 aapt2 link 只为拿 ids.txt。

    **必须把模块自己的 res.zip 也传进去**（res_zip 参数）：清单里引用了
    `@mipmap/ic_launcher`，不带就会报 "resource mipmap/ic_launcher not found"，
    拿不到 ids.txt。产物 base-r.apk 直接丢弃。
    """
    ids = os.path.join(work, "ids.txt")
    cmd = [AAPT2, "link", "-o", os.path.join(work, "base-r.apk"),
           "-I", ANDROID_JAR, "--manifest", manifest,
           "--min-sdk-version", "29", "--target-sdk-version", "35",
           "--version-code", "1", "--version-name", "1.0",
           "--auto-add-overlay", "--emit-ids", ids]
    for p in dep_packages():
        cmd += ["--extra-packages", p]
    cmd += flats
    if res_zip and os.path.isfile(res_zip):
        cmd.append(res_zip)      # 自己的 res 放最后（优先级最高，见 build.py 的说明）
    p = subprocess.run(cmd, capture_output=True)
    if p.returncode != 0:
        err = (p.stderr or b"").decode("utf-8", "replace")
        raise SystemExit("aapt2 link 失败:\n" + err[-2000:])
    return ids


JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
    "class", "const", "continue", "default", "do", "double", "else", "enum",
    "extends", "final", "finally", "float", "for", "goto", "if", "implements",
    "import", "instanceof", "int", "interface", "long", "native", "new",
    "package", "private", "protected", "public", "return", "short", "static",
    "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
    "transient", "try", "void", "volatile", "while",
}

# R 的内层类名：type 全大写（styleable 保持原样小写，AndroidX 里就这么写）
TYPE_TO_CLASS = {
    "attr": "attr", "color": "color", "dimen": "dimen", "drawable": "drawable",
    "id": "id", "integer": "integer", "string": "string", "style": "style",
    "array": "array", "bool": "bool", "interpolator": "interpolator",
    "plurals": "plurals", "styleable": "styleable", "anim": "anim",
    "animator": "animator", "font": "font", "raw": "raw", "xml": "xml",
    "mipmap": "mipmap", "layout": "layout", "menu": "menu", "navigation": "navigation",
}


def parse_ids(path):
    """{pkg: {class: [(name, value), …]}} —— 注意 aapt2 会把资源全归到 app 包名下。

    `--extra-packages` 只影响生成的 R.java，**不会**改变 emit-ids 的输出归属：
    实测所有资源都印成 `com.wodi.probe:...`。所以下面要用 AAR 自己的 R.txt
    提供的「哪些资源属于我」去反向认领。
    """
    out = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or "=" not in line:
                continue
            key, val = line.rsplit("=", 1)
            key = key.strip()
            if ":" not in key or "/" not in key:
                continue
            pkg, rest = key.split(":", 1)
            typ, name = rest.split("/", 1)
            val = val.strip()
            try:
                ival = int(val, 16) if val.startswith("0x") else int(val)
            except ValueError:
                continue
            cls = TYPE_TO_CLASS.get(typ, typ)
            out.setdefault(pkg, {}).setdefault(cls, []).append((name, ival))
    return out


def read_r_txt():
    """{pkg: {class: {name}}} —— 各 AAR 的 R.txt 声明「哪些资源是我的」。"""
    out = {}
    for f in sorted(os.listdir(DEPS)):
        if not f.endswith(".aar"):
            continue
        try:
            import zipfile
            z = zipfile.ZipFile(os.path.join(DEPS, f))
            mf = z.read("AndroidManifest.xml")
            rt = z.read("R.txt").decode("utf-8", "replace")
        except Exception:
            continue
        m = re.search(rb'package="([^"]+)"', mf)
        if not m:
            continue
        pkg = m.group(1).decode("utf-8", "replace")
        if pkg.startswith(SKIP_PKG_PREFIX):
            continue
        mine = out.setdefault(pkg, {})
        for line in rt.splitlines():
            # 格式： "int <type> <name> 0x0"（4 段；旧版 AAR 曾是 3 段）
            parts = line.split()
            if len(parts) == 4 and parts[0] == "int":
                typ, name = parts[1], parts[2]
            elif len(parts) == 3:
                typ, name = parts[0], parts[1]
            else:
                continue
            cls = TYPE_TO_CLASS.get(typ, typ)
            mine.setdefault(cls, set()).add(name)
    return out


def attribute_to_aars(ids):
    """把 app 包下的资源按 AAR 的 R.txt 认领回各自包名。

    同名资源被多个 AAR 声明时（androidx 各库常有同名 style/dimen），
    取**声明数最少**的那个 —— 它的ID 由 aapt2 一次性分配，最贴近真实归属；
    这类重复资源运行时基本用不到，认领错也不会崩。
    """
    mine = read_r_txt()
    claim = {}          # pkg -> {cls: {name: val}}
    name_owner = {}     # (cls, name) -> [pkg…]
    for pkg, classes in mine.items():
        for cls, names in classes.items():
            for n in names:
                name_owner.setdefault((cls, n), []).append(pkg)

    app = list(ids.keys())[0] if len(ids) == 1 else None
    for cls, items in ids.get(app, {}).items():
        for name, val in items:
            owners = name_owner.get((cls, name))
            if not owners:
                continue
            pkg = min(owners, key=len)
            claim.setdefault(pkg, {}).setdefault(cls, []).append((name, val))
    return claim


def gen_java(pkg, classes, out_root):
    d = os.path.join(out_root, *pkg.split("."))
    os.makedirs(d, exist_ok=True)
    path = os.path.join(d, "R.java")
    lines = ["package %s;" % pkg, "",
             "/** 由 aar_res/emit-ids 生成，勿手改。字段值来自 aapt2 --emit-ids。 */",
             "public final class R {"]
    for cls in sorted(classes):
        items = classes[cls]
        lines.append("    public static final class %s {" % cls)
        for name, ival in sorted(items, key=lambda x: x[1]):
            field = name
            if not re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", field) or field in JAVA_KEYWORDS:
                field = "_" + field
            lines.append("        public static final int %s = 0x%08x;" % (field, ival))
        lines.append("    }")
    lines.append("}")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    return path


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        print("用法: python gen_r.py <AndroidManifest> <out_java_dir> <flat_list.txt>")
        return
    manifest, out_root, flat_list = sys.argv[1], sys.argv[2], sys.argv[3]
    work = tempfile.mkdtemp(prefix="rg_r_")
    flats = [l.strip() for l in open(flat_list, encoding="utf-8") if l.strip()]
    ids = emit_ids(manifest, flats, work)
    all_ids = parse_ids(ids)
    claim = attribute_to_aars(all_ids)
    n = 0
    for pkg, classes in sorted(claim.items()):
        gen_java(pkg, classes, out_root)
        n += 1
    print("生成 R.java %d 份 -> %s" % (n, out_root))
    for pkg, classes in sorted(claim.items()):
        cnt = sum(len(v) for v in classes.values())
        print("   %-52s %d 项" % (pkg, cnt))


if __name__ == "__main__":
    main()