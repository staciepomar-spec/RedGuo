# -*- coding: utf-8 -*-
"""从依赖 AAR 提取资源与 R 类，喂给 aapt2。

**为什么需要**：Compose 运行时依赖 `androidx.customview:customview-poolingcontainer`，
它的 `PoolingContainer` 类里有 `R$id` 引用（android:id 资源 ID 常量）。
但 AAR 里**只有 R.txt（资源名清单），没有编译好的 R.class** ——
R 类正常是由 Gradle 在构建 AAR 时生成的。手工构建没有这一步，
于是运行时报 `NoClassDefFoundError: androidx/customview/poolingcontainer/R$id`。

解法：把各AAR 的 `res/` 交给 aapt2 编译，再连同模块自己的资源一起 link，
aapt2 会为所有资源统一分配 ID 并生成 R 类。这是手工构建里唯一正确的做法
（不要试图手写 R.java，资源 ID 的分配由 aapt2 决定，写死会与运行时冲突）。

用法：
  python aar_res.py extract <workdir>     # 解出各 AAR 的 res/ 与 R.txt
  python aar_res.py compile <workdir>     # aapt2 compile 每个 res 目录 -> flat zip
  python aar_res.py --list <workdir>      # 打印 flat zip 清单（一行一个）
"""
import os
import subprocess
import sys
import zipfile

TOOLS = r"D:\CEBIANLAN\File\RedGuo\_tools"
DEPS = os.path.join(TOOLS, "miuix_deps")
AAPT2 = os.path.join(TOOLS, "sdk_tmp_bt", "android-16", "aapt2.exe")


def aars():
    return [os.path.join(DEPS, f) for f in sorted(os.listdir(DEPS)) if f.endswith(".aar")]


def extract(work):
    """把每个 AAR 的 res/ 解到 <work>/aarres/<artifact>/res/，并留下 R.txt。"""
    root = os.path.join(work, "aarres")
    if os.path.isdir(root):
        import shutil
        shutil.rmtree(root)
    n = 0
    for path in aars():
        artifact = os.path.splitext(os.path.basename(path))[0]
        try:
            z = zipfile.ZipFile(path)
        except Exception:
            continue
        for name in z.namelist():
            if name.endswith("/"):
                continue
            if name == "R.txt":
                tgt = os.path.join(root, artifact, "R.txt")
                os.makedirs(os.path.dirname(tgt), exist_ok=True)
                with open(tgt, "wb") as fh:
                    fh.write(z.read(name))
            elif name.startswith("res/"):
                tgt = os.path.join(root, artifact, "res", name[4:])
                os.makedirs(os.path.dirname(tgt), exist_ok=True)
                with open(tgt, "wb") as fh:
                    fh.write(z.read(name))
                n += 1
    print("解出资源文件 %d 个 -> %s" % (n, root))
    return root


def compile_res(work):
    """aapt2 compile 每个 AAR 的 res 目录，产物是 flat_<artifact>.zip。"""
    root = os.path.join(work, "aarres")
    if not os.path.isdir(root):
        raise SystemExit("先跑 extract：" + root)
    outs = []
    for artifact in sorted(os.listdir(root)):
        res_dir = os.path.join(root, artifact, "res")
        if not os.path.isdir(res_dir):
            continue
        flat = os.path.join(work, "flat_%s.zip" % artifact)
        p = subprocess.run([AAPT2, "compile", "--dir", res_dir, "-o", flat],
                           capture_output=True)
        if p.returncode == 0 and os.path.isfile(flat):
            outs.append(flat)
        else:
            err = (p.stderr or b"").decode("utf-8", "replace").strip().splitlines()
            print("  跳过 %-34s %s" % (artifact, err[-1] if err else ""))
    with open(os.path.join(work, "flat_list.txt"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(outs))
    print("compile 成功 %d 个 -> flat_list.txt" % len(outs))
    return outs


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return
    cmd, work = sys.argv[1], sys.argv[2]
    if cmd == "extract":
        extract(work)
    elif cmd == "compile":
        compile_res(work)
    elif cmd == "--list":
        with open(os.path.join(work, "flat_list.txt"), encoding="utf-8") as fh:
            for line in fh.read().split("\n"):
                if line.strip():
                    print(line.strip())
    else:
        print(__doc__)


if __name__ == "__main__":
    main()